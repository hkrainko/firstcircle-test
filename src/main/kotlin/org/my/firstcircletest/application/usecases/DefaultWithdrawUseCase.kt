package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.*
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.WithdrawError
import org.my.firstcircletest.domain.usecases.WithdrawUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Service
class DefaultWithdrawUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionalOperator: TransactionalOperator
) : WithdrawUseCase {

    private val logger = LoggerFactory.getLogger(DefaultWithdrawUseCase::class.java)

    override suspend fun invoke(userId: UserID, amount: Long): Either<WithdrawError, Wallet> {
        return transactionalOperator.executeAndAwait { transaction ->
            either {
                ensure(userId.isNotBlank()) {
                    logger.error("Invalid user ID: $userId")
                    WithdrawError.InvalidUserId()
                }
                ensure(amount > 0) {
                    logger.error("Withdrawal amount must be positive: $amount")
                    WithdrawError.NonPositiveAmount()
                }

                val wallet = walletRepository.getWalletByUserId(userId).mapLeft {
                    logger.error("Wallet not found for user $userId")
                    WithdrawError.WalletNotFound()
                }.onRight {
                    logger.info("Retrieved wallet for user $userId")
                }.bind()

                ensure(wallet.balance >= amount) {
                    logger.warn("Withdrawal denied for user $userId: Insufficient balance. Requested: $amount, Available: ${wallet.balance}, Shortfall: ${amount - wallet.balance}")
                    transaction.setRollbackOnly()
                    WithdrawError.InsufficientBalance("Insufficient balance: requested $amount, available ${wallet.balance}")
                }

                val updatedWallet = walletRepository.updateWalletBalance(wallet.id, wallet.balance - amount)
                    .mapLeft {
                        logger.error("Wallet update failed")
                        transaction.setRollbackOnly()
                        WithdrawError.WalletUpdateFailed()
                    }.onRight {
                        logger.info("Updated wallet balance for user $userId, new balance: ${it.balance}")
                    }.bind()

                val transactions = Transaction.newTransaction(
                    walletId = wallet.id,
                    userId = wallet.userId,
                    type = TransactionType.WITHDRAWAL,
                    amount = amount,
                    status = TransactionStatus.COMPLETED
                )

                transactionRepository.create(transactions).mapLeft {
                    logger.error("Failed to create transaction for user $userId", it)
                    transaction.setRollbackOnly()
                    WithdrawError.TransactionCreationFailed()
                }.onRight {
                    logger.info("Withdrawal of $amount for user $userId completed successfully")
                }.bind()

                updatedWallet
            }
        }
    }
}
