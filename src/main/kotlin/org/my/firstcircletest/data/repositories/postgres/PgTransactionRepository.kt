package org.my.firstcircletest.data.repositories.postgres

import org.my.firstcircletest.data.repositories.postgres.dto.TransactionDTO
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.errors.DomainError
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

    override fun create(transaction: Transaction): Transaction {
        return try {
            val txDto = TransactionDTO.fromDomain(transaction)
            transactionJpaRepository.save(txDto)
            transaction
        } catch (e: Exception) {
            logger.error("PgTransactionRepo.create: error executing query", e)
            throw DomainError.DatabaseException("Error creating transaction")
        }
    }

    override fun getTransactionsByUserId(userId: String): List<Transaction> {
        return try {
            val results = transactionJpaRepository.findByUserIdOrDestinationUserId(userId.toString())
            results.map { it.toDomain() }
        } catch (e: Exception) {
            logger.error("PgTransactionRepo.getTransactionsByUserId: error executing query", e)
            throw DomainError.DatabaseException("Error retrieving transactions")
        }
    }
}

interface TransactionJpaRepository : JpaRepository<TransactionDTO, String> {
    @Query("SELECT t FROM TransactionDTO t WHERE t.userId = :userId OR t.destinationUserId = :userId ORDER BY t.createdAt DESC")
    fun findByUserIdOrDestinationUserId(userId: String): List<TransactionDTO>
}