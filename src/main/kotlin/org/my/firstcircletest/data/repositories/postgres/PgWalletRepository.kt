package org.my.firstcircletest.data.repositories.postgres

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import jakarta.persistence.EntityManager
import org.my.firstcircletest.data.repositories.postgres.dto.WalletDTO
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Repository
class PgWalletRepository(
    private val walletJpaRepository: WalletJpaRepository,
    private val entityManager: EntityManager
) : WalletRepository {
    private val logger = LoggerFactory.getLogger(PgWalletRepository::class.java)

    override fun getWalletByUserId(userId: String): Either<DomainError, Wallet> {
        return Either.catch {
            val result = walletJpaRepository.findByUserId(userId.toString())
                .orElse(null)

            if (result == null) {
                logger.error("PgWalletRepo.getWalletByUserId: no wallet found for user ID $userId")
                return DomainError.WalletNotFoundException("Wallet not found for user: $userId").left()
            }

            result.toDomain()
        }.mapLeft { e ->
            logger.error("PgWalletRepo.getWalletByUserId: error executing query", e)
            DomainError.DatabaseException("Error retrieving wallet")
        }
    }

    @Transactional
    override fun createWallet(request: CreateWalletRequest): Either<DomainError, Wallet> {
        return Either.catch {
            val walletId = "wallet-${UUID.randomUUID()}"

            val walletDTO = WalletDTO(
                id = walletId,
                userId = request.userId.toString(),
                balance = request.balance
            )

            val saved = walletJpaRepository.save(walletDTO)
            saved.toDomain()
        }.mapLeft { e ->
            logger.error("PgWalletRepo.createWallet: error executing query", e)
            DomainError.DatabaseException("Error creating wallet")
        }
    }

    @Transactional
    override fun updateWalletBalance(walletId: String, balance: Int): Either<DomainError, Wallet> {
        return Either.catch {
            val wallet = walletJpaRepository.findById(walletId.toString())
                .orElse(null)

            if (wallet == null) {
                logger.error("PgWalletRepo.updateWalletBalance: no wallet found with ID $walletId")
                return DomainError.WalletNotFoundException("Wallet not found: $walletId").left()
            }

            wallet.balance = balance
            val saved = walletJpaRepository.save(wallet)
            entityManager.flush()
            saved.toDomain()
        }.mapLeft { e ->
            logger.error("PgWalletRepo.updateWalletBalance: error executing query", e)
            DomainError.DatabaseException("Error updating wallet balance")
        }
    }
}

interface WalletJpaRepository : JpaRepository<WalletDTO, String> {
    fun findByUserId(userId: String): Optional<WalletDTO>
}
