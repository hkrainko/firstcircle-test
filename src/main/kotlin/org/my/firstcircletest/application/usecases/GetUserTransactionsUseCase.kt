package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetUserTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(GetUserTransactionsUseCase::class.java)

    @Transactional(readOnly = true)
    suspend fun invoke(userId: UserID): Either<DomainError, List<Transaction>> = either {
        ensure(userId.isNotBlank()) {
            logger.error("Invalid user ID: $userId")
            DomainError.InvalidUserIdException()
        }

        transactionRepository.getTransactionsByUserId(userId).onLeft {
            logger.error("Failed to retrieve transactions for user $userId: ${it.message}")
        }.onRight { logger.info("Retrieved ${it.size} transactions for user $userId") }.bind()
    }
}
