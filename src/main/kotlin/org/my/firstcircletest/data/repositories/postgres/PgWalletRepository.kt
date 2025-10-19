package org.my.firstcircletest.data.repositories.postgres

import arrow.core.Either
import arrow.core.left
import org.my.firstcircletest.data.repositories.postgres.entities.WalletEntity
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class PgWalletRepository(
    private val walletReactiveRepository: WalletReactiveRepository
) : WalletRepository {
    private val logger = LoggerFactory.getLogger(PgWalletRepository::class.java)

    override suspend fun getWalletByUserId(userId: String): Either<RepositoryError, Wallet> {
        return Either.catch {
            val result = walletReactiveRepository.findByUserId(userId)

            if (result == null) {
                logger.error("PgWalletRepo.getWalletByUserId: no wallet found for user ID $userId")
                return RepositoryError.NotFound("Wallet not found for user: $userId").left()
            }

            result.toDomain()
        }.mapLeft { e ->
            logger.error("PgWalletRepo.getWalletByUserId: error executing query", e)
            RepositoryError.DatabaseError("Error retrieving wallet")
        }
    }

    @Transactional
    override suspend fun createWallet(request: CreateWalletRequest): Either<RepositoryError, Wallet> {
        return Either.catch {
            val walletEntity = WalletEntity.newWallet(
                userId = request.userId,
                balance = request.balance
            )
            val saved = walletReactiveRepository.save(walletEntity)
            saved.toDomain()
        }.mapLeft { e ->
            logger.error("PgWalletRepo.createWallet: error executing query", e)
            RepositoryError.CreationFailed("Error creating wallet")
        }
    }

    @Transactional
    override suspend fun updateWalletBalance(walletId: String, balance: Long): Either<RepositoryError, Wallet> {
        return Either.catch {
            val wallet = walletReactiveRepository.findById(walletId)

            if (wallet == null) {
                logger.error("PgWalletRepo.updateWalletBalance: no wallet found with ID $walletId")
                return RepositoryError.NotFound("Wallet not found: $walletId").left()
            }

            wallet.balance = balance
            val saved = walletReactiveRepository.save(wallet)
            saved.toDomain()
        }.mapLeft { e ->
            logger.error("PgWalletRepo.updateWalletBalance: error executing query", e)
            RepositoryError.UpdateFailed("Error updating wallet balance")
        }
    }
}

interface WalletReactiveRepository : CoroutineCrudRepository<WalletEntity, String> {
    suspend fun findByUserId(userId: String): WalletEntity?
}
