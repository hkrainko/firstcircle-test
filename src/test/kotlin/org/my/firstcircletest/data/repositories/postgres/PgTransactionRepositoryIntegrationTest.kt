package org.my.firstcircletest.data.repositories.postgres

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@DataJpaTest
@ActiveProfiles("test")
@Import(PgTransactionRepository::class)
class PgTransactionRepositoryIntegrationTest {

    @Autowired
    private lateinit var pgTransactionRepository: PgTransactionRepository

    private lateinit var testUserId: UUID
    private lateinit var testWalletId: UUID
    private lateinit var testDestinationUserId: UUID
    private lateinit var testDestinationWalletId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = UUID.randomUUID()
        testWalletId = UUID.randomUUID()
        testDestinationUserId = UUID.randomUUID()
        testDestinationWalletId = UUID.randomUUID()
    }

    @Test
    fun `create should persist transaction successfully`() {
        // Given
        val transaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 10000,
            type = TransactionType.Deposit,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        // When
        val result = pgTransactionRepository.create(transaction)

        // Then
        assertNotNull(result)
        assertEquals(transaction.id, result.id)
        assertEquals(transaction.amount, result.amount)
        assertEquals(transaction.type, result.type)
        assertEquals(transaction.status, result.status)

        // Verify it was persisted
        val found = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertTrue(found.isNotEmpty())
        assertTrue(found.any { it.id == transaction.id })
    }

    @Test
    fun `create should persist transfer transaction with destination details`() {
        // Given
        val transaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            destinationWalletId = testDestinationWalletId,
            destinationUserId = testDestinationUserId,
            amount = 5000,
            type = TransactionType.Transfer,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        // When
        val result = pgTransactionRepository.create(transaction)

        // Then
        assertNotNull(result)
        assertEquals(testDestinationWalletId, result.destinationWalletId)
        assertEquals(testDestinationUserId, result.destinationUserId)
        assertEquals(TransactionType.Transfer, result.type)
    }

    @Test
    fun `getTransactionsByUserId should return transactions where user is sender`() {
        // Given
        val transaction1 = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.Deposit,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        val transaction2 = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 2000,
            type = TransactionType.Withdrawal,
            createdAt = LocalDateTime.now().plusMinutes(1),
            status = TransactionStatus.Completed
        )

        pgTransactionRepository.create(transaction1)
        pgTransactionRepository.create(transaction2)

        // When
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == transaction1.id })
        assertTrue(results.any { it.id == transaction2.id })
    }

    @Test
    fun `getTransactionsByUserId should return transactions where user is recipient`() {
        // Given
        val senderUserId = UUID.randomUUID()
        val senderWalletId = UUID.randomUUID()

        val transferTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = senderWalletId,
            userId = senderUserId,
            destinationWalletId = testWalletId,
            destinationUserId = testUserId,
            amount = 3000,
            type = TransactionType.Transfer,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        pgTransactionRepository.create(transferTransaction)

        // When
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertEquals(1, results.size)
        assertEquals(transferTransaction.id, results[0].id)
        assertEquals(testUserId, results[0].destinationUserId)
    }

    @Test
    fun `getTransactionsByUserId should return transactions ordered by createdAt desc`() {
        // Given
        val now = LocalDateTime.now()
        val transaction1 = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.Deposit,
            createdAt = now.minusHours(2),
            status = TransactionStatus.Completed
        )

        val transaction2 = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 2000,
            type = TransactionType.Deposit,
            createdAt = now.minusHours(1),
            status = TransactionStatus.Completed
        )

        val transaction3 = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 3000,
            type = TransactionType.Deposit,
            createdAt = now,
            status = TransactionStatus.Completed
        )

        pgTransactionRepository.create(transaction1)
        pgTransactionRepository.create(transaction2)
        pgTransactionRepository.create(transaction3)

        // When
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertEquals(3, results.size)
        assertEquals(transaction3.id, results[0].id) // Most recent first
        assertEquals(transaction2.id, results[1].id)
        assertEquals(transaction1.id, results[2].id)
    }

    @Test
    fun `getTransactionsByUserId should return empty list when no transactions found`() {
        // Given
        val nonExistentUserId = UUID.randomUUID()

        // When
        val results = pgTransactionRepository.getTransactionsByUserId(nonExistentUserId)

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getTransactionsByUserId should return both sent and received transactions`() {
        // Given
        val otherUserId = UUID.randomUUID()
        val otherWalletId = UUID.randomUUID()

        // Transaction sent by testUser
        val sentTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            destinationWalletId = otherWalletId,
            destinationUserId = otherUserId,
            amount = 1000,
            type = TransactionType.Transfer,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        // Transaction received by testUser
        val receivedTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = otherWalletId,
            userId = otherUserId,
            destinationWalletId = testWalletId,
            destinationUserId = testUserId,
            amount = 2000,
            type = TransactionType.Transfer,
            createdAt = LocalDateTime.now().plusMinutes(1),
            status = TransactionStatus.Completed
        )

        pgTransactionRepository.create(sentTransaction)
        pgTransactionRepository.create(receivedTransaction)

        // When
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == sentTransaction.id && it.userId == testUserId })
        assertTrue(results.any { it.id == receivedTransaction.id && it.destinationUserId == testUserId })
    }

    @Test
    fun `create should handle different transaction types correctly`() {
        // Given
        val depositTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.Deposit,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        val withdrawalTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 500,
            type = TransactionType.Withdrawal,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        // When
        pgTransactionRepository.create(depositTransaction)
        pgTransactionRepository.create(withdrawalTransaction)

        // Then
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertEquals(2, results.size)
        assertTrue(results.any { it.type == TransactionType.Deposit })
        assertTrue(results.any { it.type == TransactionType.Withdrawal })
    }

    @Test
    fun `create should handle different transaction statuses correctly`() {
        // Given
        val completedTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.Deposit,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.Completed
        )

        val pendingTransaction = Transaction(
            id = UUID.randomUUID(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 500,
            type = TransactionType.Deposit,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.PendingCancel
        )

        // When
        pgTransactionRepository.create(completedTransaction)
        pgTransactionRepository.create(pendingTransaction)

        // Then
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertEquals(2, results.size)
        assertTrue(results.any { it.status == TransactionStatus.Completed })
        assertTrue(results.any { it.status == TransactionStatus.PendingCancel })
    }
}
