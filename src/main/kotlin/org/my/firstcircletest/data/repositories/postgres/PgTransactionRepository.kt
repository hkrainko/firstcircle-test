package org.my.firstcircletest.data.repositories.postgres

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import kotlinx.coroutines.flow.toList
import org.my.firstcircletest.data.repositories.postgres.entities.TransactionEntity
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import kotlinx.coroutines.flow.Flow

@Repository
class PgTransactionRepository(
    private val transactionReactiveRepository: TransactionReactiveRepository
) : TransactionRepository {
    private val logger = LoggerFactory.getLogger(PgTransactionRepository::class.java)

    override suspend fun create(transaction: Transaction): Either<RepositoryError, Transaction> {
        return Either.catch {
            val txDto = TransactionEntity.newFromDomain(transaction)
            transactionReactiveRepository.save(txDto)
            transaction
        }.mapLeft { e ->
            logger.error("PgTransactionRepo.create: error executing query", e)
            RepositoryError.CreationFailed("Error creating transaction")
        }
    }

    override suspend fun getTransactionsByUserId(userId: String): Either<RepositoryError, List<Transaction>> {
        return Either.catch {
            transactionReactiveRepository.findByUserIdOrDestinationUserId(userId).toList()
        }.mapLeft { e ->
            logger.error("PgTransactionRepo.getTransactionsByUserId: error executing query", e)
            RepositoryError.RetrievalFailed("Error retrieving transactions")
        }.flatMap { dtos ->
            val transactions = mutableListOf<Transaction>()
            for (dto in dtos) {
                when (val result = dto.toDomain()) {
                    is Either.Left -> return@flatMap result
                    is Either.Right -> transactions.add(result.value)
                }
            }
            transactions.right()
        }
    }
}

interface TransactionReactiveRepository : CoroutineCrudRepository<TransactionEntity, String> {
    @Query("SELECT * FROM transactions WHERE user_id = :userId OR destination_user_id = :userId ORDER BY created_at DESC")
    fun findByUserIdOrDestinationUserId(userId: String): Flow<TransactionEntity>
}