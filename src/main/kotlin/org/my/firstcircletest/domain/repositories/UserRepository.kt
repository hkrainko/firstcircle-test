package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.UserID

interface UserRepository {
    suspend fun createUser(request: CreateUserRequest): Either<RepositoryError, User>
    suspend fun getUserById(userId: UserID): Either<RepositoryError, User>
}
