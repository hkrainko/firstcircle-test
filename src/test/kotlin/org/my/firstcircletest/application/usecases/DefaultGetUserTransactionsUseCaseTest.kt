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
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.usecases.GetUserTransactionsError
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.time.LocalDateTime

class DefaultGetUserTransactionsUseCaseTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val transactionalOperator: TransactionalOperator = mockk()
    private val reactiveTransaction: ReactiveTransaction = mockk(relaxed = true)
    private lateinit var useCase: DefaultGetUserTransactionsUseCase

    @BeforeEach
    fun setup() {
        mockkStatic("org.springframework.transaction.reactive.TransactionalOperatorExtensionsKt")
        coEvery { transactionalOperator.executeAndAwait(any<suspend (ReactiveTransaction) -> Any?>()) } coAnswers {
            val action = arg<suspend (ReactiveTransaction) -> Any?>(1)
            action.invoke(reactiveTransaction)
        }

        useCase = DefaultGetUserTransactionsUseCase(transactionRepository, transactionalOperator)
    }

    @Test
    fun `should successfully retrieve user transactions`() = runTest {
        // Given
        val userId = "user123"
        val transactions = listOf(
            Transaction(
                id = "tx1",
                walletId = "wallet123",
                userId = userId,
                type = TransactionType.DEPOSIT,
                amount = 1000,
                status = TransactionStatus.COMPLETED,
                createdAt = LocalDateTime.now()
            ),
            Transaction(
                id = "tx2",
                walletId = "wallet123",
                userId = userId,
                type = TransactionType.WITHDRAWAL,
                amount = 500,
                status = TransactionStatus.COMPLETED,
                createdAt = LocalDateTime.now()
            )
        )

        coEvery { transactionRepository.getTransactionsByUserId(userId) } returns transactions.right()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { txList ->
                assertEquals(2, txList.size)
                assertEquals(transactions, txList)
            }
        )

        coVerify { transactionRepository.getTransactionsByUserId(userId) }
    }

    @Test
    fun `should return empty list when user has no transactions`() = runTest {
        // Given
        val userId = "user123"
        val emptyTransactions = emptyList<Transaction>()

        coEvery { transactionRepository.getTransactionsByUserId(userId) } returns emptyTransactions.right()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { txList ->
                assertTrue(txList.isEmpty())
            }
        )
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
                assertTrue(error is GetUserTransactionsError.InvalidUserId)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { transactionRepository.getTransactionsByUserId(any()) }
    }

    @Test
    fun `should return TransactionRetrievalFailed when repository fails`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RepositoryError.DatabaseError("Database connection failed")

        coEvery { transactionRepository.getTransactionsByUserId(userId) } returns repositoryError.left()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is GetUserTransactionsError.TransactionRetrievalFailed)
            },
            ifRight = { }
        )

        coVerify { transactionRepository.getTransactionsByUserId(userId) }
    }

    @Test
    fun `should handle multiple transaction types`() = runTest {
        // Given
        val userId = "user123"
        val transactions = listOf(
            Transaction(
                id = "tx1",
                walletId = "wallet123",
                userId = userId,
                type = TransactionType.DEPOSIT,
                amount = 1000,
                status = TransactionStatus.COMPLETED,
                createdAt = LocalDateTime.now()
            ),
            Transaction(
                id = "tx2",
                walletId = "wallet123",
                userId = userId,
                type = TransactionType.WITHDRAWAL,
                amount = 500,
                status = TransactionStatus.COMPLETED,
                createdAt = LocalDateTime.now()
            ),
            Transaction(
                id = "tx3",
                walletId = "wallet123",
                userId = userId,
                type = TransactionType.TRANSFER,
                amount = 200,
                status = TransactionStatus.COMPLETED,
                createdAt = LocalDateTime.now(),
                destinationWalletId = "wallet456",
                destinationUserId = "user456"
            )
        )

        coEvery { transactionRepository.getTransactionsByUserId(userId) } returns transactions.right()

        // When
        val result = useCase.invoke(userId)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { txList ->
                assertEquals(3, txList.size)
            }
        )
    }
}
