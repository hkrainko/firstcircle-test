package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class TransferUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(TransferUseCase::class.java)

    @Transactional
    suspend fun invoke(transfer: Transfer): Either<DomainError, Transfer> = either {
        ensure(transfer.fromUserId.isNotBlank() && transfer.toUserId.isNotBlank()) {
            logger.error("Invalid user IDs: ${transfer.fromUserId} or ${transfer.toUserId}")
            DomainError.InvalidUserIdException()
        }
        ensure(transfer.amount > 0) {
            logger.error("Transfer amount must be positive: ${transfer.amount}")
            DomainError.NonPositiveAmountException()
        }
        ensure(transfer.fromUserId != transfer.toUserId) {
            logger.error("Transfer from and to the same user: ${transfer.fromUserId}")
            DomainError.SameUserTransferException()
        }

        // 1. Update sender's wallet (withdraw)
        val fromWallet = walletRepository.getWalletByUserId(transfer.fromUserId)
        ensure(fromWallet != null) {
            logger.error("Wallet not found for user ${transfer.fromUserId}")
            DomainError.WalletNotFoundException()
        }
        ensure(fromWallet.balance >= transfer.amount) {
            logger.error("Insufficient balance for user ${transfer.fromUserId}: requested ${transfer.amount}, available ${fromWallet.balance}")
            DomainError.InsufficientBalanceException()
        }
        val updatedFromWallet = walletRepository.updateWalletBalance(fromWallet.id, fromWallet.balance - transfer.amount)
        logger.info("Deducted ${transfer.amount} from user ${transfer.fromUserId}")

        // 2. Update receiver's wallet (deposit)
        val toWallet = walletRepository.getWalletByUserId(transfer.toUserId)
        ensure(toWallet != null) {
            logger.error("Wallet not found for user ${transfer.toUserId}")
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            DomainError.WalletNotFoundException()
        }
        val updatedToWallet = Either.catch {
            walletRepository.updateWalletBalance(toWallet.id, toWallet.balance + transfer.amount)
        }.mapLeft {
            logger.error("Failed to update wallet balance for user ${transfer.toUserId}", it)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            DomainError.WalletUpdateException()
        }.bind()
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

        Either.catch {
            transactionRepository.create(transaction)
        }.mapLeft {
            logger.error("Failed to create transaction for transfer from ${transfer.fromUserId} to ${transfer.toUserId}", it)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            DomainError.TransactionCreationException()
        }.bind()

        logger.info("Transfer of ${transfer.amount} from user ${transfer.fromUserId} to user ${transfer.toUserId} completed successfully")

        transfer
    }
}
