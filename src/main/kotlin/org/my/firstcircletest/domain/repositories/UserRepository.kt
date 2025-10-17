package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.errors.DomainError

interface UserRepository {
    fun createUser(request: CreateUserRequest): Either<DomainError, User>
}
