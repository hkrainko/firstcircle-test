package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.GetWalletInfoError
import org.my.firstcircletest.domain.usecases.GetWalletInfoUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Service
class DefaultGetWalletInfoUseCase(
    private val walletRepository: WalletRepository,
    private val transactionalOperator: TransactionalOperator
) : GetWalletInfoUseCase {

    private val logger = LoggerFactory.getLogger(DefaultGetWalletInfoUseCase::class.java)

    override suspend fun invoke(userId: UserID): Either<GetWalletInfoError, Wallet> {
        return transactionalOperator.executeAndAwait { transaction ->
            either {
                ensure(userId.isNotBlank()) {
                    logger.error("Invalid user ID: $userId")
                    GetWalletInfoError.InvalidUserId()
                }

                walletRepository.getWalletByUserId(userId).mapLeft {
                    logger.error("Failed to retrieve wallet for user $userId: ${it.message}")
                    GetWalletInfoError.WalletNotFound()
                }.onRight { logger.info("Retrieved wallet for user $userId") }.bind()
            }
        }
    }
}
