package org.my.firstcircletest.application.usecases

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

@Service
class DepositUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(DepositUseCase::class.java)

    @Transactional
    suspend fun invoke(userId: UserID, amount: Int): Wallet {
        if (userId.isBlank()) {
            throw DomainError.InvalidUserIdException()
        }
        if (amount <= 0) {
            throw DomainError.NonPositiveAmountException()
        }

        try {
            val wallet = walletRepository.getWalletByUserId(userId)
            if (wallet == null) {
                logger.error("Wallet not found for user $userId")
                throw DomainError.WalletNotFoundException()
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

            transactionRepository.create(transaction)
            logger.info("Deposit successful for user $userId, new balance: ${updatedWallet.balance}")

            return updatedWallet
        } catch (e: Exception) {
            logger.error("Error processing deposit for user $userId: ${e.message}", e)
            throw e
        }
    }
}
