package org.my.firstcircletest.application.usecases

import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransferUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(TransferUseCase::class.java)

    @Transactional
    suspend fun invoke(transfer: Transfer): Transfer {
        if (transfer.fromUserId.isBlank() || transfer.toUserId.isBlank()) {
            logger.error("Invalid user IDs: ${transfer.fromUserId} or ${transfer.toUserId}")
            throw DomainError.InvalidUserIdException()
        }
        if (transfer.amount <= 0) {
            logger.error("Transfer amount must be positive: ${transfer.amount}")
            throw DomainError.NonPositiveAmountException()
        }
        if (transfer.fromUserId == transfer.toUserId) {
            logger.error("Transfer from and to the same user: ${transfer.fromUserId}")
            throw DomainError.SameUserTransferException()
        }

        try {
            // 1. Update sender's wallet (withdraw)
            val fromWallet = walletRepository.getWalletByUserId(transfer.fromUserId)
            if (fromWallet == null) {
                logger.error("Wallet not found for user ${transfer.fromUserId}")
                throw DomainError.WalletNotFoundException()
            }
            if (fromWallet.balance < transfer.amount) {
                logger.error("Insufficient balance for user ${transfer.fromUserId}: requested ${transfer.amount}, available ${fromWallet.balance}")
                throw DomainError.InsufficientBalanceException()
            }
            val updatedFromWallet = walletRepository.updateWalletBalance(fromWallet.id, fromWallet.balance - transfer.amount)
            logger.info("Deducted ${transfer.amount} from user ${transfer.fromUserId}")

            // 2. Update receiver's wallet (deposit)
            val toWallet = walletRepository.getWalletByUserId(transfer.toUserId)
            if (toWallet == null) {
                logger.error("Wallet not found for user ${transfer.toUserId}")
                throw DomainError.WalletNotFoundException()
            }
            val updatedToWallet = walletRepository.updateWalletBalance(toWallet.id, toWallet.balance + transfer.amount)
            logger.info("Added ${transfer.amount} to user ${transfer.toUserId}")

            // 3. Create a single transfer transaction record
            val transaction = Transaction.newTransferTransaction(
                sourceWalletId = updatedFromWallet.id,
                userId = transfer.fromUserId,
                destinationWalletId = updatedToWallet.id,
                destinationUserId = transfer.toUserId,
                amount = transfer.amount,
                status = TransactionStatus.COMPLETED
            )

            transactionRepository.create(transaction)
            logger.info("Transfer of ${transfer.amount} from user ${transfer.fromUserId} to user ${transfer.toUserId} completed successfully")

            return transfer
        } catch (e: Exception) {
            logger.error("Error executing transfer from ${transfer.fromUserId} to ${transfer.toUserId}: ${e.message}", e)
            throw e
        }
    }
}
