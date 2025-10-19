package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.usecases.GetUserTransactionsError
import org.my.firstcircletest.domain.usecases.GetUserTransactionsUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Service
class DefaultGetUserTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
    private val transactionalOperator: TransactionalOperator
) : GetUserTransactionsUseCase {

    private val logger = LoggerFactory.getLogger(DefaultGetUserTransactionsUseCase::class.java)

    override suspend fun invoke(userId: UserID): Either<GetUserTransactionsError, List<Transaction>> {
        return transactionalOperator.executeAndAwait { transaction ->
            either {
                ensure(userId.isNotBlank()) {
                    logger.error("Invalid user ID: $userId")
                    GetUserTransactionsError.InvalidUserId()
                }

                transactionRepository.getTransactionsByUserId(userId).mapLeft {
                    logger.error("Failed to retrieve transactions for user $userId: ${it.message}")
                    GetUserTransactionsError.TransactionRetrievalFailed()
                }.onRight { logger.info("Retrieved ${it.size} transactions for user $userId") }.bind()
            }
        }
    }
}
