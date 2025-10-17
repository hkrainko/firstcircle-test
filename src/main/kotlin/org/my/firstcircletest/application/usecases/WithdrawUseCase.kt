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
class WithdrawUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(WithdrawUseCase::class.java)

    @Transactional
    suspend fun invoke(userId: UserID, amount: Int): Wallet {
        if (userId.isBlank()) {
            logger.error("Invalid user ID: $userId")
            throw DomainError.InvalidUserIdException()
        }
        if (amount <= 0) {
            logger.error("Withdrawal amount must be positive: $amount")
            throw DomainError.NonPositiveAmountException()
        }

        try {
            val wallet = walletRepository.getWalletByUserId(userId)
            if (wallet == null) {
                logger.error("Wallet not found for user $userId")
                throw DomainError.WalletNotFoundException()
            }

            logger.info("Retrieved wallet for user $userId")

            if (wallet.balance < amount) {
                logger.error("Insufficient balance for user $userId: requested $amount, available ${wallet.balance}")
                throw DomainError.InsufficientBalanceException()
            }

            val updatedWallet = walletRepository.updateWalletBalance(wallet.id, wallet.balance - amount)
            logger.info("Updated wallet balance for user $userId, new balance: ${updatedWallet.balance}")

            val transactions = Transaction.newTransaction(
                walletId = wallet.id,
                userId = wallet.userId,
                type = TransactionType.WITHDRAWAL,
                amount = amount,
                status = TransactionStatus.COMPLETED
            )

            transactionRepository.create(transactions)
            logger.info("Withdrawal of $amount for user $userId completed successfully")

            return updatedWallet
        } catch (e: Exception) {
            logger.error("Error executing withdrawal for user $userId: ${e.message}", e)
            throw e
        }
    }
}
