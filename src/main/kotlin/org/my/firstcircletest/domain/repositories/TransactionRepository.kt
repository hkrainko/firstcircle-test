package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.Transaction

interface TransactionRepository {
    fun create(transaction: Transaction): Either<RepositoryError, Transaction>
    fun getTransactionsByUserId(userId: String): Either<RepositoryError, List<Transaction>>
}
