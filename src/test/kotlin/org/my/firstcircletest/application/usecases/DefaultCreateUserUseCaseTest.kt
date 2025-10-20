package org.my.firstcircletest.application.usecases

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.UserRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.CreateUserError
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

class DefaultCreateUserUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val walletRepository: WalletRepository = mockk()
    private val transactionalOperator: TransactionalOperator = mockk()
    private val reactiveTransaction: ReactiveTransaction = mockk(relaxed = true)
    private lateinit var useCase: DefaultCreateUserUseCase

    @BeforeEach
    fun setup() {
        mockkStatic("org.springframework.transaction.reactive.TransactionalOperatorExtensionsKt")
        coEvery { transactionalOperator.executeAndAwait(any<suspend (ReactiveTransaction) -> Any?>()) } coAnswers {
            val action = arg<suspend (ReactiveTransaction) -> Any?>(1)
            action.invoke(reactiveTransaction)
        }

        useCase = DefaultCreateUserUseCase(userRepository, walletRepository, transactionalOperator)
    }

    @Test
    fun `should successfully create user with wallet and initial balance`() = runTest {
        // Given
        val request = CreateUserRequest(
            name = "John Doe",
            initBalance = 1000
        )
        val user = User(id = "user123", name = "John Doe")
        val walletRequest = CreateWalletRequest(userId = user.id, balance = 1000)
        val wallet = Wallet(id = "wallet123", userId = user.id, balance = 1000)

        coEvery { userRepository.createUser(request) } returns user.right()
        coEvery { walletRepository.createWallet(walletRequest) } returns wallet.right()

        // When
        val result = useCase.invoke(request)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { createdUserResponse ->
                assertEquals(user.id, createdUserResponse.user.id)
                assertEquals(user.name, createdUserResponse.user.name)
                assertEquals(wallet.id, createdUserResponse.wallet.id)
                assertEquals(wallet.userId, createdUserResponse.wallet.userId)
                assertEquals(wallet.balance, createdUserResponse.wallet.balance)
            }
        )

        coVerify { userRepository.createUser(request) }
        coVerify { walletRepository.createWallet(walletRequest) }
    }

    @Test
    fun `should return UserCreationFailed when user repository fails`() = runTest {
        // Given
        val request = CreateUserRequest(
            name = "John Doe",
            initBalance = 1000
        )
        val repositoryError = RepositoryError.DatabaseError("Database connection failed")

        coEvery { userRepository.createUser(request) } returns repositoryError.left()

        // When
        val result = useCase.invoke(request)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is CreateUserError.UserCreationFailed)
            },
            ifRight = { }
        )

        coVerify { userRepository.createUser(request) }
        coVerify(exactly = 0) { walletRepository.createWallet(any()) }
    }

    @Test
    fun `should return WalletCreationFailed when wallet repository fails`() = runTest {
        // Given
        val request = CreateUserRequest(
            name = "John Doe",
            initBalance = 1000
        )
        val user = User(id = "user123", name = "John Doe")
        val walletRequest = CreateWalletRequest(userId = user.id, balance = 1000)
        val repositoryError = RepositoryError.DatabaseError("Wallet creation failed")

        coEvery { userRepository.createUser(request) } returns user.right()
        coEvery { walletRepository.createWallet(walletRequest) } returns repositoryError.left()

        // When
        val result = useCase.invoke(request)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is CreateUserError.WalletCreationFailed)
            },
            ifRight = { }
        )

        coVerify { userRepository.createUser(request) }
        coVerify { walletRepository.createWallet(walletRequest) }
    }

    @Test
    fun `should create wallet with zero initial balance`() = runTest {
        // Given
        val request = CreateUserRequest(
            name = "John Doe",
            initBalance = 0
        )
        val user = User(id = "user123", name = "John Doe")
        val walletRequest = CreateWalletRequest(userId = user.id, balance = 0)
        val wallet = Wallet(id = "wallet123", userId = user.id, balance = 0)

        coEvery { userRepository.createUser(request) } returns user.right()
        coEvery { walletRepository.createWallet(walletRequest) } returns wallet.right()

        // When
        val result = useCase.invoke(request)

        // Then
        assertTrue(result.isRight())
        coVerify { walletRepository.createWallet(walletRequest) }
    }
}
