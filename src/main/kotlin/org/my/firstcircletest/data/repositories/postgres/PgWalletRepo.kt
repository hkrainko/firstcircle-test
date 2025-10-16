package org.my.firstcircletest.data.repositories.postgres

import jakarta.persistence.EntityManager
import jakarta.persistence.NoResultException
import org.my.firstcircletest.data.repositories.postgres.dto.WalletDTO
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class PgWalletRepo(
    private val entityManager: EntityManager
) {
    private val logger = LoggerFactory.getLogger(PgWalletRepo::class.java)

    @Transactional(readOnly = true)
    fun getWalletByUserId(userId: UUID): Wallet {
        return try {
            val query = """
                SELECT id, user_id, balance 
                FROM wallets 
                WHERE user_id = :userId
            """.trimIndent()

            val result = entityManager.createNativeQuery(query, WalletDTO::class.java)
                .setParameter("userId", userId.toString())
                .singleResult as WalletDTO

            result.toDomain()
        } catch (e: NoResultException) {
            logger.error("PgWalletRepo.getWalletByUserId: no wallet found for user ID $userId")
            throw DomainError.WalletNotFoundException("Wallet not found for user: $userId")
        } catch (e: Exception) {
            logger.error("PgWalletRepo.getWalletByUserId: error executing query", e)
            throw DomainError.DatabaseException("Error retrieving wallet")
        }
    }

    @Transactional
    fun createWallet(request: CreateWalletRequest): Wallet {
        return try {
            val walletId = "wallet-${UUID.randomUUID()}"

            val query = """
                INSERT INTO wallets (id, user_id, balance) 
                VALUES (:id, :userId, :balance)
            """.trimIndent()

            entityManager.createNativeQuery(query)
                .setParameter("id", walletId)
                .setParameter("userId", request.userId.toString())
                .setParameter("balance", request.balance)
                .executeUpdate()

            // Retrieve the created wallet
            val selectQuery = """
                SELECT id, user_id, balance 
                FROM wallets 
                WHERE id = :id
            """.trimIndent()

            val result = entityManager.createNativeQuery(selectQuery, WalletDTO::class.java)
                .setParameter("id", walletId)
                .singleResult as WalletDTO

            result.toDomain()
        } catch (e: Exception) {
            logger.error("PgWalletRepo.createWallet: error executing query", e)
            throw DomainError.DatabaseException("Error creating wallet")
        }
    }

    @Transactional
    fun updateWalletBalance(walletId: UUID, balance: Long): Wallet {
        return try {
            val query = """
                UPDATE wallets 
                SET balance = :balance 
                WHERE id = :walletId
            """.trimIndent()

            val updatedRows = entityManager.createNativeQuery(query)
                .setParameter("balance", balance)
                .setParameter("walletId", walletId.toString())
                .executeUpdate()

            if (updatedRows == 0) {
                logger.error("PgWalletRepo.updateWalletBalance: no wallet found with ID $walletId")
                throw DomainError.WalletNotFoundException("Wallet not found: $walletId")
            }

            // Retrieve the updated wallet
            val selectQuery = """
                SELECT id, user_id, balance 
                FROM wallets 
                WHERE id = :walletId
            """.trimIndent()

            val result = entityManager.createNativeQuery(selectQuery, WalletDTO::class.java)
                .setParameter("walletId", walletId.toString())
                .singleResult as WalletDTO

            result.toDomain()
        } catch (e: NoResultException) {
            logger.error("PgWalletRepo.updateWalletBalance: no wallet found with ID $walletId")
            throw DomainError.WalletNotFoundException("Wallet not found: $walletId")
        } catch (e: DomainError) {
            throw e
        } catch (e: Exception) {
            logger.error("PgWalletRepo.updateWalletBalance: error executing query", e)
            throw DomainError.DatabaseException("Error updating wallet balance")
        }
    }
}
