package org.my.firstcircletest.data.repositories.postgres

import jakarta.persistence.EntityManager
import org.my.firstcircletest.data.repositories.postgres.dto.TransactionDTO
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class PgTransactionRepo(
    private val entityManager: EntityManager
) {
    private val logger = LoggerFactory.getLogger(PgTransactionRepo::class.java)

    @Transactional
    fun create(transaction: Transaction): Transaction {
        return try {
            val txDto = TransactionDTO.fromDomain(transaction)

            if (transaction.type == TransactionType.Transfer) {
                val query = """
                    INSERT INTO transactions (id, wallet_id, user_id, destination_wallet_id, destination_user_id, amount, type, status) 
                    VALUES (:id, :walletId, :userId, :destinationWalletId, :destinationUserId, :amount, :type, :status)
                """.trimIndent()

                entityManager.createNativeQuery(query)
                    .setParameter("id", txDto.id)
                    .setParameter("walletId", txDto.walletId)
                    .setParameter("userId", txDto.userId)
                    .setParameter("destinationWalletId", txDto.destinationWalletId)
                    .setParameter("destinationUserId", txDto.destinationUserId)
                    .setParameter("amount", txDto.amount)
                    .setParameter("type", txDto.type)
                    .setParameter("status", txDto.status)
                    .executeUpdate()
            } else {
                val query = """
                    INSERT INTO transactions (id, wallet_id, user_id, amount, type, status) 
                    VALUES (:id, :walletId, :userId, :amount, :type, :status)
                """.trimIndent()

                entityManager.createNativeQuery(query)
                    .setParameter("id", txDto.id)
                    .setParameter("walletId", txDto.walletId)
                    .setParameter("userId", txDto.userId)
                    .setParameter("amount", txDto.amount)
                    .setParameter("type", txDto.type)
                    .setParameter("status", txDto.status)
                    .executeUpdate()
            }

            transaction
        } catch (e: Exception) {
            logger.error("PgTransactionRepo.create: error executing query", e)
            throw DomainError.DatabaseException("Error creating transaction")
        }
    }

    @Transactional(readOnly = true)
    fun getTransactionsByUserId(userId: UUID): List<Transaction> {
        return try {
            val query = """
                SELECT id, wallet_id, user_id, destination_wallet_id, destination_user_id, amount, type, created_at, updated_at, status 
                FROM transactions 
                WHERE user_id = :userId OR destination_user_id = :userId 
                ORDER BY created_at DESC
            """.trimIndent()

            @Suppress("UNCHECKED_CAST")
            val results = entityManager.createNativeQuery(query, TransactionDTO::class.java)
                .setParameter("userId", userId.toString())
                .resultList as List<TransactionDTO>

            results.map { it.toDomain() }
        } catch (e: Exception) {
            logger.error("PgTransactionRepo.getTransactionsByUserId: error executing query", e)
            throw DomainError.DatabaseException("Error retrieving transactions")
        }
    }
}
