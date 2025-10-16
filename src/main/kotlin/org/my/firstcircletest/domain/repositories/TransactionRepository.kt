package org.my.firstcircletest.domain.repositories

import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.UserID

interface TransactionRepository {
    fun create(transaction: Transaction): Transaction
    fun getTransactionsByUserId(userId: UserID): List<Transaction>
}
