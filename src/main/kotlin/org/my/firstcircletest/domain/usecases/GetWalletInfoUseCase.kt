package org.my.firstcircletest.domain.usecases

import arrow.core.Either
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet

interface GetWalletInfoUseCase {
    suspend fun invoke(userId: UserID): Either<GetWalletInfoError, Wallet>
}

sealed class GetWalletInfoError(open val message: String) {

    data class InvalidUserId(
        override val message: String = "User ID cannot be blank"
    ) : GetWalletInfoError(message)

    data class WalletNotFound(
        override val message: String = "Wallet not found for user"
    ) : GetWalletInfoError(message)
}
