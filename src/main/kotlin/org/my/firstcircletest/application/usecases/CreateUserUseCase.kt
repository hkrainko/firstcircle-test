package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.UserRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository
) {

    private val logger = LoggerFactory.getLogger(CreateUserUseCase::class.java)

    @Transactional
    suspend fun invoke(request: CreateUserRequest): Either<DomainError, User> = either {
        val user = userRepository.createUser(request).onLeft {
            logger.error("Failed to create user: ${it.message}")
        }.bind()

        logger.info("User created successfully: ${user.id}")

        createWalletForNewUser(user.id).onLeft {
            logger.error("Failed to create wallet for user: ${user.id}, marking transaction for rollback")
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        }.bind()

        logger.info("Wallet created for user: ${user.id}")

        user
    }

    private fun createWalletForNewUser(userId: UserID): Either<DomainError, Unit> = either {
        val request = CreateWalletRequest(
            userId = userId,
            balance = 0
        )
        walletRepository.createWallet(request)
    }
}
