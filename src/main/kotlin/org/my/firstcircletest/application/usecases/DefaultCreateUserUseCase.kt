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
        val user = userRepository.createUser(request).mapLeft {
            logger.error("Failed to create user: ${it.message}")
            CreateUserError.UserCreationFailed()
        }.bind()

        logger.info("User created successfully: ${user.id}")

        createWalletForNewUser(user.id, request.initBalance).onLeft {
            logger.error("Failed to create wallet for user: ${user.id}, marking transaction for rollback")
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        }.bind()

        logger.info("Wallet created for user: ${user.id} with initial balance: ${request.initBalance}")

        user
    }

    private fun createWalletForNewUser(userId: UserID, initBalance: Int): Either<CreateUserError, Unit> = either {
        val request = CreateWalletRequest(
            userId = userId,
            balance = initBalance
        )
        walletRepository.createWallet(request).mapLeft {
            CreateUserError.WalletCreationFailed()
        }.bind()
    }
}
