package org.my.firstcircletest.application.usecases

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
    suspend fun invoke(userId: UserID): List<Transaction> {
        if (userId.isBlank()) {
            logger.error("Invalid user ID: $userId")
            throw DomainError.InvalidUserIdException()
        }

        try {
            val transactions = transactionRepository.getTransactionsByUserId(userId)
            logger.info("Retrieved ${transactions.size} transactions for user $userId")
            return transactions
        } catch (e: Exception) {
            logger.error("Error retrieving transactions for user $userId: ${e.message}", e)
            throw e
        }
    }
}
