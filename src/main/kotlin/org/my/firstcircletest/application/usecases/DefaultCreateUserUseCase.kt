package org.my.firstcircletest.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.repositories.UserRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.CreateUserError
import org.my.firstcircletest.domain.usecases.CreateUserUseCase
import org.my.firstcircletest.domain.usecases.DepositUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class DefaultCreateUserUseCase(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
) : CreateUserUseCase {

    private val logger = LoggerFactory.getLogger(DefaultCreateUserUseCase::class.java)

    @Transactional
    override suspend fun invoke(request: CreateUserRequest): Either<CreateUserError, User> = either {
        logger.info("Creating user with name: ${request.name} and initial balance: ${request.initBalance}")

        val user = userRepository.createUser(request).mapLeft { repositoryError ->
            logger.error("Failed to create user '${request.name}': ${repositoryError.message}", repositoryError)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            CreateUserError.UserCreationFailed("Failed to create user: ${repositoryError.message}")
        }.bind()

        logger.info("User created successfully: ${user.id}")

        createWalletForNewUser(user.id, request.initBalance).onLeft { walletError ->
            logger.error("Failed to create wallet for user: ${user.id}, marking transaction for rollback. Error: ${walletError.message}")
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        }.bind()

        logger.info("Wallet created successfully for user: ${user.id} with initial balance: ${request.initBalance}")

        user
    }

    private fun createWalletForNewUser(userId: UserID, initBalance: Int): Either<CreateUserError, Unit> = either {
        val request = CreateWalletRequest(
            userId = userId,
            balance = initBalance
        )
        walletRepository.createWallet(request).mapLeft { repositoryError ->
            logger.error("Wallet creation failed for user $userId: ${repositoryError.message}", repositoryError)
            CreateUserError.WalletCreationFailed("Failed to create wallet: ${repositoryError.message}")
        }.bind()
    }
}
