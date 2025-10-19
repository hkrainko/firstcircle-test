package org.my.firstcircletest.application.usecases

import arrow.core.left
import arrow.core.right
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.WithdrawError
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.executeAndAwait
import reactor.core.publisher.Mono

class DefaultWithdrawUseCaseTest {

    private val walletRepository: WalletRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val transactionalOperator: TransactionalOperator = mockk()
    private val reactiveTransaction: ReactiveTransaction = mockk(relaxed = true)
    private lateinit var useCase: DefaultWithdrawUseCase

    @BeforeEach
    fun setup() {
        mockkStatic("org.springframework.transaction.reactive.TransactionalOperatorExtensionsKt")
        coEvery { transactionalOperator.executeAndAwait(any<suspend (ReactiveTransaction) -> Any?>()) } coAnswers {
            val action = arg<suspend (ReactiveTransaction) -> Any?>(1)
            action.invoke(reactiveTransaction)
        }

        useCase = DefaultWithdrawUseCase(walletRepository, transactionRepository, transactionalOperator)
    }

    @Test
    fun `should successfully withdraw amount from wallet`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500L
        val initialBalance = 1000L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = initialBalance)
        val updatedWallet = wallet.copy(balance = initialBalance - amount)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()
        coEvery { walletRepository.updateWalletBalance(wallet.id, initialBalance - amount) } returns updatedWallet.right()
        coEvery { transactionRepository.create(any()) } returns mockk<org.my.firstcircletest.domain.entities.Transaction>().right()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { resultWallet ->
                assertEquals(500, resultWallet.balance)
                assertEquals(userId, resultWallet.userId)
            }
        )

        coVerify { walletRepository.getWalletByUserId(userId) }
        coVerify { walletRepository.updateWalletBalance(wallet.id, initialBalance - amount) }
        coVerify { transactionRepository.create(any()) }
    }

    @Test
    fun `should return InvalidUserId when userId is blank`() = runTest {
        // Given
        val userId = ""
        val amount = 500L

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.InvalidUserId)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.getWalletByUserId(any()) }
    }

    @Test
    fun `should return NonPositiveAmount when amount is zero`() = runTest {
        // Given
        val userId = "user123"
        val amount = 0L

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.NonPositiveAmount)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.getWalletByUserId(any()) }
    }

    @Test
    fun `should return NonPositiveAmount when amount is negative`() = runTest {
        // Given
        val userId = "user123"
        val amount = -100L

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.NonPositiveAmount)
            },
            ifRight = { }
        )
    }

    @Test
    fun `should return WalletNotFound when wallet does not exist`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500L
        val repositoryError = RepositoryError.NotFound("Wallet not found")

        coEvery { walletRepository.getWalletByUserId(userId) } returns repositoryError.left()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.WalletNotFound)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(userId) }
        coVerify(exactly = 0) { walletRepository.updateWalletBalance(any(), any()) }
    }

    @Test
    fun `should return InsufficientBalance when balance is less than amount`() = runTest {
        // Given
        val userId = "user123"
        val amount = 1500L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1000)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.InsufficientBalance)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(userId) }
        coVerify(exactly = 0) { walletRepository.updateWalletBalance(any(), any()) }
    }

    @Test
    fun `should return WalletUpdateFailed when wallet update fails`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1000)
        val repositoryError = RepositoryError.DatabaseError("Update failed")

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()
        coEvery { walletRepository.updateWalletBalance(wallet.id, wallet.balance - amount) } returns repositoryError.left()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.WalletUpdateFailed)
            },
            ifRight = { }
        )

        coVerify { walletRepository.updateWalletBalance(wallet.id, wallet.balance - amount) }
        coVerify(exactly = 0) { transactionRepository.create(any()) }
    }

    @Test
    fun `should return TransactionCreationFailed when transaction creation fails`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1000)
        val updatedWallet = wallet.copy(balance = 500)
        val repositoryError = RepositoryError.DatabaseError("Transaction creation failed")

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()
        coEvery { walletRepository.updateWalletBalance(wallet.id, wallet.balance - amount) } returns updatedWallet.right()
        coEvery { transactionRepository.create(any()) } returns repositoryError.left()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.TransactionCreationFailed)
            },
            ifRight = { }
        )

        coVerify { transactionRepository.create(any()) }
    }

    @Test
    fun `should allow withdrawal of entire balance`() = runTest {
        // Given
        val userId = "user123"
        val amount = 1000L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1000)
        val updatedWallet = wallet.copy(balance = 0)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()
        coEvery { walletRepository.updateWalletBalance(wallet.id, 0) } returns updatedWallet.right()
        coEvery { transactionRepository.create(any()) } returns mockk<org.my.firstcircletest.domain.entities.Transaction>().right()

        // When
        val result = useCase.invoke(userId, amount)

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
    fun `should prevent overdraft when trying to withdraw 1100 from 1000 balance`() = runTest {
        // Given
        val userId = "user123"
        val amount = 1100L
        val initialBalance = 1000L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = initialBalance)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.InsufficientBalance)
                assertTrue(error.message.contains("Insufficient balance"))
            },
            ifRight = { }
        )

        // Verify no wallet update or transaction creation occurred
        coVerify { walletRepository.getWalletByUserId(userId) }
        coVerify(exactly = 0) { walletRepository.updateWalletBalance(any(), any()) }
        coVerify(exactly = 0) { transactionRepository.create(any()) }
    }

    @Test
    fun `should prevent overdraft with minimal shortfall`() = runTest {
        // Given
        val userId = "user123"
        val amount = 1001L
        val initialBalance = 1000L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = initialBalance)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.InsufficientBalance)
                assertEquals("Insufficient balance: requested 1001, available 1000", error.message)
            },
            ifRight = { }
        )
    }

    @Test
    fun `should handle large overdraft attempt`() = runTest {
        // Given
        val userId = "user123"
        val amount = 10000L
        val initialBalance = 100L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = initialBalance)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.InsufficientBalance)
            },
            ifRight = { }
        )
    }

    @Test
    fun `should prevent overdraft when balance is zero`() = runTest {
        // Given
        val userId = "user123"
        val amount = 100L
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 0)

        coEvery { walletRepository.getWalletByUserId(userId) } returns wallet.right()

        // When
        val result = useCase.invoke(userId, amount)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is WithdrawError.InsufficientBalance)
                assertEquals("Insufficient balance: requested 100, available 0", error.message)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.updateWalletBalance(any(), any()) }
    }
}
