package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User

interface UserRepository {
    fun createUser(request: CreateUserRequest): Either<RepositoryError, User>
}
