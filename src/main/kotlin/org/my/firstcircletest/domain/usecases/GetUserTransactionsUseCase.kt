package org.my.firstcircletest.domain.usecases

import arrow.core.Either
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.UserID

interface GetUserTransactionsUseCase {
    suspend fun invoke(userId: UserID): Either<GetUserTransactionsError, List<Transaction>>
}

sealed class GetUserTransactionsError(open val message: String) {
    data class InvalidUserId(override val message: String = "User ID cannot be blank") :
        GetUserTransactionsError(message)

    data class TransactionRetrievalFailed(override val message: String = "Failed to retrieve transactions") :
        GetUserTransactionsError(message)
}
