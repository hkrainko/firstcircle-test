package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class DepositUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(DepositUseCase::class.java)

    @Transactional
    suspend fun invoke(userId: UserID, amount: Int): Either<DomainError, Wallet> = either {
        ensure(userId.isNotBlank()) {
            DomainError.InvalidUserIdException()
        }
        ensure(amount > 0) {
            DomainError.NonPositiveAmountException()
        }

        val wallet = walletRepository.getWalletByUserId(userId)
        ensure(wallet != null) {
            logger.error("Wallet not found for user $userId")
            DomainError.WalletNotFoundException()
        }

        logger.info("Retrieved wallet for user $userId")

        val updatedWallet = walletRepository.updateWalletBalance(wallet.id, wallet.balance + amount)
        logger.info("Updated wallet balance for user $userId, new balance: ${updatedWallet.balance}")

        val transaction = Transaction.newTransaction(
            walletId = wallet.id,
            userId = wallet.userId,
            type = TransactionType.DEPOSIT,
            amount = amount,
            status = TransactionStatus.COMPLETED,
        )

        Either.catch {
            transactionRepository.create(transaction)
        }.mapLeft {
            logger.error("Failed to create transaction for user $userId", it)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            DomainError.TransactionCreationException()
        }.bind()

        logger.info("Deposit successful for user $userId, new balance: ${updatedWallet.balance}")

        updatedWallet
    }
}
