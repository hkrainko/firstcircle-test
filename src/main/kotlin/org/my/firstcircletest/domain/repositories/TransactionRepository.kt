package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.errors.DomainError

interface TransactionRepository {
    fun create(transaction: Transaction): Either<DomainError, Transaction>
    fun getTransactionsByUserId(userId: String): Either<DomainError, List<Transaction>>
}
