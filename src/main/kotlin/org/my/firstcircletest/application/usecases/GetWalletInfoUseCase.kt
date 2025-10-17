package org.my.firstcircletest.application.usecases

import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetWalletInfoUseCase(
    private val walletRepository: WalletRepository
) {

    private val logger = LoggerFactory.getLogger(GetWalletInfoUseCase::class.java)

    @Transactional(readOnly = true)
    suspend fun invoke(userId: UserID): Wallet {
        if (userId.isBlank()) {
            logger.error("Invalid user ID: $userId")
            throw DomainError.InvalidUserIdException()
        }

        try {
            val wallet = walletRepository.getWalletByUserId(userId)
            if (wallet == null) {
                logger.error("Wallet not found for user $userId")
                throw DomainError.WalletNotFoundException()
            }
            logger.info("Retrieved wallet for user $userId")
            return wallet
        } catch (e: Exception) {
            logger.error("Error retrieving wallet for user $userId: ${e.message}", e)
            throw e
        }
    }
}
