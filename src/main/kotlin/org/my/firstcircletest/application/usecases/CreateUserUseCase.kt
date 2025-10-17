package org.my.firstcircletest.application.usecases

import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.repositories.UserRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository
) {

    private val logger = LoggerFactory.getLogger(CreateUserUseCase::class.java)

    @Transactional
    suspend fun invoke(request: CreateUserRequest): User {
        try {
            val user = userRepository.createUser(request)
            logger.info("User created successfully: ${user.id}")

            createWalletForNewUser(user.id)
            logger.info("Wallet created for user: ${user.id}")

            return user
        } catch (e: Exception) {
            logger.error("Error creating user: ${e.message}", e)
            throw e
        }
    }

    private fun createWalletForNewUser(userId: UserID) {
        try {
            val request = CreateWalletRequest(
                userId = userId,
                balance = 0
            )
            walletRepository.createWallet(request)
        } catch (e: Exception) {
            logger.error("Error creating wallet for user $userId: ${e.message}", e)
            throw e
        }
    }
}
