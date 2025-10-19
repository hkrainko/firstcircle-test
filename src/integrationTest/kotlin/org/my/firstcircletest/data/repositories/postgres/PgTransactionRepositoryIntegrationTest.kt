package org.my.firstcircletest.data.repositories.postgres

import arrow.core.getOrElse
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class PgTransactionRepositoryIntegrationTest {

    @Autowired
    private lateinit var pgTransactionRepository: PgTransactionRepository

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private lateinit var testUserId: String
    private lateinit var testWalletId: String
    private lateinit var testDestinationUserId: String
    private lateinit var testDestinationWalletId: String

    @BeforeEach
    fun setUp() = runTest {
        testUserId = "user-${UUID.randomUUID()}"
        testWalletId = "wallet-${UUID.randomUUID()}"
        testDestinationUserId = "user-${UUID.randomUUID()}"
        testDestinationWalletId = "wallet-${UUID.randomUUID()}"

        // Create test users
        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, testUserId)
            .bind(1, "Test User $testUserId")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, testDestinationUserId)
            .bind(1, "Destination User $testDestinationUserId")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        // Create test wallets
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, testWalletId)
            .bind(1, testUserId)
            .bind(2, 100000L)
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, testDestinationWalletId)
            .bind(1, testDestinationUserId)
            .bind(2, 50000L)
            .fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `create should persist transaction successfully`() = runTest {
        // Given
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 10000,
            type = TransactionType.DEPOSIT,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        // When
        val eitherResult = pgTransactionRepository.create(transaction)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(transaction.id, result.id)
        assertEquals(transaction.amount, result.amount)
        assertEquals(transaction.type, result.type)
        assertEquals(transaction.status, result.status)

        // Verify it was persisted
        val eitherFound = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertTrue(eitherFound.isRight())
        val found = eitherFound.getOrElse { fail("Expected Right but got Left") }
        assertTrue(found.isNotEmpty())
        assertTrue(found.any { it.id == transaction.id })
    }

    @Test
    fun `create should persist transfer transaction with destination details`() = runTest {
        // Given
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            destinationWalletId = testDestinationWalletId,
            destinationUserId = testDestinationUserId,
            amount = 5000,
            type = TransactionType.TRANSFER,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        // When
        val eitherResult = pgTransactionRepository.create(transaction)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(testDestinationWalletId, result.destinationWalletId)
        assertEquals(testDestinationUserId, result.destinationUserId)
        assertEquals(TransactionType.TRANSFER, result.type)
    }

    @Test
    fun `getTransactionsByUserId should return transactions where user is sender`() = runTest {
        // Given
        val transaction1 = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.DEPOSIT,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        val transaction2 = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 2000,
            type = TransactionType.WITHDRAWAL,
            createdAt = LocalDateTime.now().plusMinutes(1),
            status = TransactionStatus.COMPLETED
        )

        pgTransactionRepository.create(transaction1)
        pgTransactionRepository.create(transaction2)

        // When
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == transaction1.id })
        assertTrue(results.any { it.id == transaction2.id })
    }

    @Test
    fun `getTransactionsByUserId should return transactions where user is recipient`() = runTest {
        // Given
        val senderUserId = "user-${UUID.randomUUID()}"
        val senderWalletId = "wallet-${UUID.randomUUID()}"

        // Create sender user and wallet
        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, senderUserId)
            .bind(1, "Sender User $senderUserId")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, senderWalletId)
            .bind(1, senderUserId)
            .bind(2, 100000L)
            .fetch().rowsUpdated().awaitSingle()

        val transferTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = senderWalletId,
            userId = senderUserId,
            destinationWalletId = testWalletId,
            destinationUserId = testUserId,
            amount = 3000,
            type = TransactionType.TRANSFER,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        pgTransactionRepository.create(transferTransaction)

        // When
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertEquals(1, results.size)
        assertEquals(transferTransaction.id, results[0].id)
        assertEquals(testUserId, results[0].destinationUserId)
    }

    @Test
    fun `getTransactionsByUserId should return transactions ordered by createdAt desc`() = runTest {
        // Given
        val now = LocalDateTime.now()
        val transaction1 = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.DEPOSIT,
            createdAt = now.minusHours(2),
            status = TransactionStatus.COMPLETED
        )

        val transaction2 = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 2000,
            type = TransactionType.DEPOSIT,
            createdAt = now.minusHours(1),
            status = TransactionStatus.COMPLETED
        )

        val transaction3 = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 3000,
            type = TransactionType.DEPOSIT,
            createdAt = now,
            status = TransactionStatus.COMPLETED
        )

        pgTransactionRepository.create(transaction1)
        pgTransactionRepository.create(transaction2)
        pgTransactionRepository.create(transaction3)

        // When
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertEquals(3, results.size)
        assertEquals(transaction3.id, results[0].id) // Most recent first
        assertEquals(transaction2.id, results[1].id)
        assertEquals(transaction1.id, results[2].id)
    }

    @Test
    fun `getTransactionsByUserId should return empty list when no transactions found`() = runTest {
        // Given
        val nonExistentUserId = "user-${UUID.randomUUID()}"

        // When
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(nonExistentUserId)

        // Then
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getTransactionsByUserId should return both sent and received transactions`() = runTest {
        // Given
        val otherUserId = "user-${UUID.randomUUID()}"
        val otherWalletId = "wallet-${UUID.randomUUID()}"

        // Create other user and wallet
        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, otherUserId)
            .bind(1, "Other User $otherUserId")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, otherWalletId)
            .bind(1, otherUserId)
            .bind(2, 75000L)
            .fetch().rowsUpdated().awaitSingle()

        // Transaction sent by testUser
        val sentTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            destinationWalletId = otherWalletId,
            destinationUserId = otherUserId,
            amount = 1000,
            type = TransactionType.TRANSFER,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        // Transaction received by testUser
        val receivedTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = otherWalletId,
            userId = otherUserId,
            destinationWalletId = testWalletId,
            destinationUserId = testUserId,
            amount = 2000,
            type = TransactionType.TRANSFER,
            createdAt = LocalDateTime.now().plusMinutes(1),
            status = TransactionStatus.COMPLETED
        )

        pgTransactionRepository.create(sentTransaction)
        pgTransactionRepository.create(receivedTransaction)

        // When
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == sentTransaction.id && it.userId == testUserId })
        assertTrue(results.any { it.id == receivedTransaction.id && it.destinationUserId == testUserId })
    }

    @Test
    fun `create should handle different transaction types correctly`() = runTest {
        // Given
        val depositTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.DEPOSIT,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        val withdrawalTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 500,
            type = TransactionType.WITHDRAWAL,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        // When
        pgTransactionRepository.create(depositTransaction)
        pgTransactionRepository.create(withdrawalTransaction)

        // Then
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertEquals(2, results.size)
        assertTrue(results.any { it.type == TransactionType.DEPOSIT })
        assertTrue(results.any { it.type == TransactionType.WITHDRAWAL })
    }

    @Test
    fun `create should handle different transaction statuses correctly`() = runTest {
        // Given
        val completedTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.DEPOSIT,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        val pendingTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 500,
            type = TransactionType.DEPOSIT,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.PENDING_CANCEL
        )

        // When
        pgTransactionRepository.create(completedTransaction)
        pgTransactionRepository.create(pendingTransaction)

        // Then
        val eitherResults = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertTrue(eitherResults.isRight())
        val results = eitherResults.getOrElse { fail("Expected Right but got Left") }
        assertEquals(2, results.size)
        assertTrue(results.any { it.status == TransactionStatus.COMPLETED })
        assertTrue(results.any { it.status == TransactionStatus.PENDING_CANCEL })
    }
}
