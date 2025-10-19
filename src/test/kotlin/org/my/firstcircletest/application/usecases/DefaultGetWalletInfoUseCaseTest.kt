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
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.GetWalletInfoError
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

class DefaultGetWalletInfoUseCaseTest {

    private val walletRepository: WalletRepository = mockk()
    private val transactionalOperator: TransactionalOperator = mockk()
    private val reactiveTransaction: ReactiveTransaction = mockk(relaxed = true)
    private lateinit var useCase: DefaultGetWalletInfoUseCase

    @BeforeEach
    fun setup() {
        mockkStatic("org.springframework.transaction.reactive.TransactionalOperatorExtensionsKt")
        coEvery { transactionalOperator.executeAndAwait(any<suspend (ReactiveTransaction) -> Any?>()) } coAnswers {
            val action = arg<suspend (ReactiveTransaction) -> Any?>(1)
            action.invoke(reactiveTransaction)
        }

        useCase = DefaultGetWalletInfoUseCase(walletRepository, transactionalOperator)
    }

    @Test
    fun `should successfully retrieve wallet information`() = runTest {
        // Given
        val userId = "user123"
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1000)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { resultWallet ->
                assertEquals(wallet.id, resultWallet.id)
                assertEquals(userId, resultWallet.userId)
                assertEquals(1000, resultWallet.balance)
            }
        )

        coVerify { walletRepository.getWalletByUserId(userId) }
    }

    @Test
    fun `should return InvalidUserId when userId is blank`() = runTest {
        // Given
        val userId = ""

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is GetWalletInfoError.InvalidUserId)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.getWalletByUserId(any()) }
    }

    @Test
    fun `should return InvalidUserId when userId is whitespace only`() = runTest {
        // Given
        val userId = "   "

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is GetWalletInfoError.InvalidUserId)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.getWalletByUserId(any()) }
    }

    @Test
    fun `should return WalletNotFound when wallet does not exist`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RepositoryError.NotFound("Wallet not found")

        coEvery { walletRepository.getWalletByUserId(userId) } returns repositoryError.left()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is GetWalletInfoError.WalletNotFound)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(userId) }
    }

    @Test
    fun `should return WalletNotFound when repository has database error`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RepositoryError.DatabaseError("Database connection failed")

        coEvery { walletRepository.getWalletByUserId(userId) } returns repositoryError.left()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is GetWalletInfoError.WalletNotFound)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(userId) }
    }

    @Test
    fun `should retrieve wallet with zero balance`() = runTest {
        // Given
        val userId = "user123"
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 0)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { resultWallet ->
                assertEquals(0, resultWallet.balance)
            }
        )
    }

    @Test
    fun `should retrieve wallet with large balance`() = runTest {
        // Given
        val userId = "user123"
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 999999999)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { resultWallet ->
                assertEquals(999999999, resultWallet.balance)
            }
        )
    }
}
