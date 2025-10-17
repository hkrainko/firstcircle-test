package org.my.firstcircletest.data.repositories.postgres

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import org.my.firstcircletest.data.repositories.postgres.dto.TransactionDTO
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
class PgTransactionRepository(
    private val transactionJpaRepository: TransactionJpaRepository
) : TransactionRepository {
    private val logger = LoggerFactory.getLogger(PgTransactionRepository::class.java)

    override fun create(transaction: Transaction): Either<RepositoryError, Transaction> {
        return Either.catch {
            val txDto = TransactionDTO.fromDomain(transaction)
            transactionJpaRepository.save(txDto)
            transaction
        }.mapLeft { e ->
            logger.error("PgTransactionRepo.create: error executing query", e)
            RepositoryError.CreationFailed("Error creating transaction")
        }
    }

    override fun getTransactionsByUserId(userId: String): Either<RepositoryError, List<Transaction>> {
        return Either.catch {
            transactionJpaRepository.findByUserIdOrDestinationUserId(userId)
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

interface TransactionJpaRepository : JpaRepository<TransactionDTO, String> {
    @Query("SELECT t FROM TransactionDTO t WHERE t.userId = :userId OR t.destinationUserId = :userId ORDER BY t.createdAt DESC")
    fun findByUserIdOrDestinationUserId(userId: String): List<TransactionDTO>
}