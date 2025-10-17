package org.my.firstcircletest.domain.usecases

import arrow.core.Either
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet

interface DepositUseCase {
    suspend fun invoke(userId: UserID, amount: Int): Either<DepositError, Wallet>
}

sealed class DepositError(open val message: String) {
    data class InvalidUserId(override val message: String = "User ID cannot be blank") : DepositError(message)
    data class NonPositiveAmount(override val message: String = "Deposit amount must be positive") :
        DepositError(message)

    data class WalletNotFound(override val message: String = "Wallet not found for user") : DepositError(message)
    data class TransactionCreationFailed(override val message: String = "Failed to create deposit transaction") :
        DepositError(message)

    data class WalletUpdateFailed(override val message: String = "Failed to update wallet balance") :
        DepositError(message)
}
