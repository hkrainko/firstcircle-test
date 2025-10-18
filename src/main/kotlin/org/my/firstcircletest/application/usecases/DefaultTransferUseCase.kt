package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.TransferError
import org.my.firstcircletest.domain.usecases.TransferUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class DefaultTransferUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository
) : TransferUseCase {

    private val logger = LoggerFactory.getLogger(DefaultTransferUseCase::class.java)

    @Transactional
    override suspend fun invoke(transfer: Transfer): Either<TransferError, Transfer> = either {
        ensure(transfer.fromUserId.isNotBlank() && transfer.toUserId.isNotBlank()) {
            logger.error("Invalid user IDs: ${transfer.fromUserId} or ${transfer.toUserId}")
            TransferError.InvalidUserId()
        }
        ensure(transfer.amount > 0) {
            logger.error("Transfer amount must be positive: ${transfer.amount}")
            TransferError.NonPositiveAmount()
        }
        ensure(transfer.fromUserId != transfer.toUserId) {
            logger.error("Transfer from and to the same user: ${transfer.fromUserId}")
            TransferError.SameUserTransfer()
        }

        val fromWallet = walletRepository.getWalletByUserId(transfer.fromUserId).mapLeft {
            logger.error("Failed to retrieve wallet for user ${transfer.fromUserId}", it)
            TransferError.WalletNotFound()
        }.bind()

        ensure(fromWallet.balance >= transfer.amount) {
            logger.error("Insufficient balance for user ${transfer.fromUserId}: requested ${transfer.amount}, available ${fromWallet.balance}")
            TransferError.InsufficientBalance()
        }

        val updatedFromWallet =
            walletRepository.updateWalletBalance(fromWallet.id, fromWallet.balance - transfer.amount)
                .mapLeft {
                    logger.error("Failed to update wallet for user ${transfer.fromUserId}: $it")
                    TransferError.WalletUpdateFailed()
                }
                .onRight { logger.info("Deducted ${transfer.amount} from user ${transfer.fromUserId}") }
                .bind()

        val toWallet = walletRepository.getWalletByUserId(transfer.toUserId).mapLeft {
            logger.error("Wallet not found for user ${transfer.toUserId}")
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            TransferError.WalletNotFound()
        }.bind()

        val updatedToWallet = walletRepository.updateWalletBalance(toWallet.id, toWallet.balance + transfer.amount)
            .mapLeft {
                logger.error("Failed to update wallet balance for user ${transfer.toUserId}", it)
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
                TransferError.WalletUpdateFailed()
            }.onRight {
                logger.info("Added ${transfer.amount} to user ${transfer.toUserId}")
            }
            .bind()

        val transaction = Transaction.newTransferTransaction(
            sourceWalletId = updatedFromWallet.id,
            userId = transfer.fromUserId,
            destinationWalletId = updatedToWallet.id,
            destinationUserId = transfer.toUserId,
            amount = transfer.amount,
            status = TransactionStatus.COMPLETED
        )

        transactionRepository.create(transaction)
            .mapLeft {
                logger.error(
                    "Failed to create transaction for transfer from ${transfer.fromUserId} to ${transfer.toUserId}",
                    it
                )
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
                TransferError.TransactionCreationFailed()
            }.onRight {
                logger.info("Transfer of ${transfer.amount} from user ${transfer.fromUserId} to user ${transfer.toUserId} completed successfully")
            }.bind()

        transfer
    }
}
