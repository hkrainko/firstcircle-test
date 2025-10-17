package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.DepositError
import org.my.firstcircletest.domain.usecases.DepositUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class DefaultDepositUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) : DepositUseCase {

    private val logger = LoggerFactory.getLogger(DefaultDepositUseCase::class.java)

    @Transactional
    override suspend fun invoke(userId: UserID, amount: Int): Either<DepositError, Wallet> = either {
        ensure(userId.isNotBlank()) { DepositError.InvalidUserId() }
        ensure(amount > 0) { DepositError.NonPositiveAmount() }

        val wallet = walletRepository.getWalletByUserId(userId).mapLeft {
            logger.error("Failed to retrieve wallet for user $userId: ${it.message}")
            DepositError.WalletNotFound()
        }.bind()

        logger.info("Retrieved wallet for user $userId")

        val updatedWallet = walletRepository.updateWalletBalance(wallet.id, wallet.balance + amount)
            .mapLeft {
                logger.error("Failed to update wallet balance for user $userId: ${it.message}")
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
                DepositError.WalletUpdateFailed()
            }.onRight { logger.info("Updated wallet balance for user $userId, new balance: ${it.balance}") }.bind()

        val transaction = Transaction.newTransaction(
            walletId = wallet.id,
            userId = wallet.userId,
            type = TransactionType.DEPOSIT,
            amount = amount,
            status = TransactionStatus.COMPLETED,
        )

        transactionRepository.create(transaction).mapLeft {
            logger.error("Failed to create transaction for user $userId", it)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            DepositError.TransactionCreationFailed()
        }.onRight { logger.info("Deposit successful for user $userId, new balance: ${updatedWallet.balance}") }.bind()


        updatedWallet
    }
}
