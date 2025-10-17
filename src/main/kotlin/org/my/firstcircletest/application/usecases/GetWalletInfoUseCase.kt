package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
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
    suspend fun invoke(userId: UserID): Either<DomainError, Wallet> = either {
        ensure(userId.isNotBlank()) {
            logger.error("Invalid user ID: $userId")
            DomainError.InvalidUserIdException()
        }

        val wallet = walletRepository.getWalletByUserId(userId)
        ensure(wallet != null) {
            logger.error("Wallet not found for user $userId")
            DomainError.WalletNotFoundException()
        }
        logger.info("Retrieved wallet for user $userId")
        wallet
    }
}
