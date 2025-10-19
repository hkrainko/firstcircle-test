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
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Service
class DefaultTransferUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionalOperator: TransactionalOperator
) : TransferUseCase {

    private val logger = LoggerFactory.getLogger(DefaultTransferUseCase::class.java)

    override suspend fun invoke(transfer: Transfer): Either<TransferError, Transfer> {
        return transactionalOperator.executeAndAwait { transaction ->
            either {
                logger.info("Initiating transfer: ${transfer.amount} from user ${transfer.fromUserId} to user ${transfer.toUserId}")

                ensure(transfer.fromUserId.isNotBlank() && transfer.toUserId.isNotBlank()) {
                    logger.error("Invalid user IDs: fromUserId='${transfer.fromUserId}', toUserId='${transfer.toUserId}'")
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

                val fromWallet = walletRepository.getWalletByUserId(transfer.fromUserId).mapLeft { repositoryError ->
                    logger.error("Failed to retrieve source wallet for user ${transfer.fromUserId}: ${repositoryError.message}", repositoryError)
                    TransferError.WalletNotFound("Source wallet not found for user ${transfer.fromUserId}")
                }.bind()

                ensure(fromWallet.balance >= transfer.amount) {
                    logger.warn("Transfer denied for user ${transfer.fromUserId}: Insufficient balance. Requested: ${transfer.amount}, Available: ${fromWallet.balance}, Shortfall: ${transfer.amount - fromWallet.balance}")
                    TransferError.InsufficientBalance("Insufficient balance: requested ${transfer.amount}, available ${fromWallet.balance}")
                }

                val updatedFromWallet =
                    walletRepository.updateWalletBalance(fromWallet.id, fromWallet.balance - transfer.amount)
                        .mapLeft { repositoryError ->
                            logger.error("Failed to update source wallet for user ${transfer.fromUserId}: ${repositoryError.message}", repositoryError)
                            transaction.setRollbackOnly()
                            TransferError.WalletUpdateFailed("Failed to deduct from source wallet: ${repositoryError.message}")
                        }
                        .onRight { logger.info("Deducted ${transfer.amount} from user ${transfer.fromUserId}, new balance: ${it.balance}") }
                        .bind()

                val toWallet = walletRepository.getWalletByUserId(transfer.toUserId).mapLeft {
                    logger.error("Wallet not found for user ${transfer.toUserId}")
                    transaction.setRollbackOnly()
                    TransferError.WalletNotFound()
                }.bind()

                val updatedToWallet = walletRepository.updateWalletBalance(toWallet.id, toWallet.balance + transfer.amount)
                    .mapLeft {
                        logger.error("Failed to update wallet balance for user ${transfer.toUserId}", it)
                        transaction.setRollbackOnly()
                        TransferError.WalletUpdateFailed()
                    }.onRight {
                        logger.info("Added ${transfer.amount} to user ${transfer.toUserId}")
                    }
                    .bind()

                val transactions = Transaction.newTransferTransaction(
                    sourceWalletId = updatedFromWallet.id,
                    userId = transfer.fromUserId,
                    destinationWalletId = updatedToWallet.id,
                    destinationUserId = transfer.toUserId,
                    amount = transfer.amount,
                    status = TransactionStatus.COMPLETED
                )

                transactionRepository.create(transactions)
                    .mapLeft {
                        logger.error(
                            "Failed to create transaction for transfer from ${transfer.fromUserId} to ${transfer.toUserId}",
                            it
                        )
                        transaction.setRollbackOnly()
                        TransferError.TransactionCreationFailed()
                    }.onRight {
                        logger.info("Transfer of ${transfer.amount} from user ${transfer.fromUserId} to user ${transfer.toUserId} completed successfully")
                    }.bind()

                transfer
            }
        }
    }
}
