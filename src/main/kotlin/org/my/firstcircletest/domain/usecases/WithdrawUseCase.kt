package org.my.firstcircletest.domain.usecases

import arrow.core.Either
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet

interface WithdrawUseCase {
    suspend fun invoke(userId: UserID, amount: Int): Either<WithdrawError, Wallet>
}

sealed class WithdrawError(open val message: String) {
    data class InvalidUserId(override val message: String = "User ID cannot be blank") : WithdrawError(message)
    data class NonPositiveAmount(override val message: String = "Withdrawal amount must be positive") :
        WithdrawError(message)

    data class WalletNotFound(override val message: String = "Wallet not found for user") : WithdrawError(message)
    data class InsufficientBalance(
        val requested: Int,
        val available: Int,
        override val message: String = "Insufficient balance: requested $requested, available $available"
    ) : WithdrawError(message)

    data class TransactionCreationFailed(override val message: String = "Failed to create withdrawal transaction") :
        WithdrawError(message)

    data class WalletUpdateFailed(override val message: String = "Failed to update wallet balance") :
        WithdrawError(message)
}
