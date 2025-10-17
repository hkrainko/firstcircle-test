package org.my.firstcircletest.domain.usecases

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User


interface CreateUserUseCase {
    suspend fun invoke(request: CreateUserRequest): Either<CreateUserError, User>
}

sealed class CreateUserError(open val message: String) {
    data class UserCreationFailed(
        override val message: String = "Failed to create user"
    ) : CreateUserError(message)

    data class WalletCreationFailed(
        override val message: String = "Failed to create wallet for user"
    ) : CreateUserError(message)
}
