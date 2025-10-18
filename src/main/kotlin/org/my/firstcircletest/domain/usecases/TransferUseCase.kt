package org.my.firstcircletest.domain.usecases

import arrow.core.Either
import org.my.firstcircletest.domain.entities.Transfer

interface TransferUseCase {
    suspend fun invoke(transfer: Transfer): Either<TransferError, Transfer>
}

sealed class TransferError(open val message: String) {
    data class InvalidUserId(
        override val message: String = "User ID cannot be blank"
    ) : TransferError(message)

    data class NonPositiveAmount(
        override val message: String = "Transfer amount must be positive"
    ) : TransferError(message)

    data class SameUserTransfer(
        override val message: String = "Cannot transfer to the same user"
    ) : TransferError(message)

    data class WalletNotFound(
        override val message: String = "Wallet not found for user"
    ) : TransferError(message)

    data class InsufficientBalance(
        override val message: String = "Insufficient balance"
    ) : TransferError(message)

    data class WalletUpdateFailed(
        override val message: String = "Failed to update wallet balance"
    ) : TransferError(message)

    data class TransactionCreationFailed(
        override val message: String = "Failed to create transfer transaction"
    ) : TransferError(message)
}
