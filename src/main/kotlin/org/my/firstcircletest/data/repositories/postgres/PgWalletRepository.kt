package org.my.firstcircletest.data.repositories.postgres

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
    private val walletJpaRepository: WalletJpaRepository
) : WalletRepository {
    private val logger = LoggerFactory.getLogger(PgWalletRepository::class.java)

    override fun getWalletByUserId(userId: UUID): Wallet {
        return try {
            val result = walletJpaRepository.findByUserId(userId.toString())
                .orElseThrow {
                    DomainError.WalletNotFoundException("Wallet not found for user: $userId")
                }

            result.toDomain()
        } catch (e: DomainError) {
            logger.error("PgWalletRepo.getWalletByUserId: no wallet found for user ID $userId")
            throw e
        } catch (e: Exception) {
            logger.error("PgWalletRepo.getWalletByUserId: error executing query", e)
            throw DomainError.DatabaseException("Error retrieving wallet")
        }
    }

    @Transactional
    override fun createWallet(request: CreateWalletRequest): Wallet {
        return try {
            val walletId = "wallet-${UUID.randomUUID()}"

            val walletDTO = WalletDTO(
                id = walletId,
                userId = request.userId.toString(),
                balance = request.balance
            )

            val saved = walletJpaRepository.save(walletDTO)
            saved.toDomain()
        } catch (e: Exception) {
            logger.error("PgWalletRepo.createWallet: error executing query", e)
            throw DomainError.DatabaseException("Error creating wallet")
        }
    }

    override fun updateWalletBalance(walletId: UUID, balance: Int): Wallet {
        return try {
            val wallet = walletJpaRepository.findById(walletId.toString())
                .orElseThrow {
                    DomainError.WalletNotFoundException("Wallet not found: $walletId")
                }

            wallet.balance = balance
            val saved = walletJpaRepository.save(wallet)
            saved.toDomain()
        } catch (e: DomainError) {
            logger.error("PgWalletRepo.updateWalletBalance: no wallet found with ID $walletId")
            throw e
        } catch (e: Exception) {
            logger.error("PgWalletRepo.updateWalletBalance: error executing query", e)
            throw DomainError.DatabaseException("Error updating wallet balance")
        }
    }
}

interface WalletJpaRepository : JpaRepository<WalletDTO, String> {
    fun findByUserId(userId: String): Optional<WalletDTO>
}
