package org.my.firstcircletest.data.repositories.postgres

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
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

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var testUserId: String
    private lateinit var testWalletId: String
    private lateinit var testDestinationUserId: String
    private lateinit var testDestinationWalletId: String

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"
        testWalletId = "wallet-${UUID.randomUUID()}"
        testDestinationUserId = "user-${UUID.randomUUID()}"
        testDestinationWalletId = "wallet-${UUID.randomUUID()}"

        // Create test users
        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, testUserId.toString())
            setParameter(2, "Test User ${testUserId}")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, testDestinationUserId.toString())
            setParameter(2, "Destination User ${testDestinationUserId}")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        // Create test wallets
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, testWalletId.toString())
            setParameter(2, testUserId.toString())
            setParameter(3, 100000L)
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, testDestinationWalletId.toString())
            setParameter(2, testDestinationUserId.toString())
            setParameter(3, 50000L)
        }.executeUpdate()

        entityManager.flush()
    }

    @Test
    fun `create should persist transaction successfully`() {
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
        val result = pgTransactionRepository.create(transaction)

        // Then
        assertNotNull(result)
        assertEquals(testDestinationWalletId, result.destinationWalletId)
        assertEquals(testDestinationUserId, result.destinationUserId)
        assertEquals(TransactionType.TRANSFER, result.type)
    }

    @Test
    fun `getTransactionsByUserId should return transactions where user is sender`() {
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
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == transaction1.id })
        assertTrue(results.any { it.id == transaction2.id })
    }

    @Test
    fun `getTransactionsByUserId should return transactions where user is recipient`() {
        // Given
        val senderUserId = "user-${UUID.randomUUID()}"
        val senderWalletId = "wallet-${UUID.randomUUID()}"

        // Create sender user and wallet
        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, senderUserId.toString())
            setParameter(2, "Sender User ${senderUserId}")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, senderWalletId.toString())
            setParameter(2, senderUserId.toString())
            setParameter(3, 100000L)
        }.executeUpdate()
        entityManager.flush()

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
        val nonExistentUserId = "user-${UUID.randomUUID()}"

        // When
        val results = pgTransactionRepository.getTransactionsByUserId(nonExistentUserId)

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getTransactionsByUserId should return both sent and received transactions`() {
        // Given
        val otherUserId = "user-${UUID.randomUUID()}"
        val otherWalletId = "wallet-${UUID.randomUUID()}"

        // Create other user and wallet
        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, otherUserId.toString())
            setParameter(2, "Other User ${otherUserId}")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, otherWalletId.toString())
            setParameter(2, otherUserId.toString())
            setParameter(3, 75000L)
        }.executeUpdate()
        entityManager.flush()

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
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == sentTransaction.id && it.userId == testUserId })
        assertTrue(results.any { it.id == receivedTransaction.id && it.destinationUserId == testUserId })
    }

    @Test
    fun `create should handle different transaction types correctly`() {
        // Given
        val DEPOSITTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 1000,
            type = TransactionType.DEPOSIT,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        val WITHDRAWALTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            walletId = testWalletId,
            userId = testUserId,
            amount = 500,
            type = TransactionType.WITHDRAWAL,
            createdAt = LocalDateTime.now(),
            status = TransactionStatus.COMPLETED
        )

        // When
        pgTransactionRepository.create(DEPOSITTransaction)
        pgTransactionRepository.create(WITHDRAWALTransaction)

        // Then
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertEquals(2, results.size)
        assertTrue(results.any { it.type == TransactionType.DEPOSIT })
        assertTrue(results.any { it.type == TransactionType.WITHDRAWAL })
    }

    @Test
    fun `create should handle different transaction statuses correctly`() {
        // Given
        val COMPLETEDTransaction = Transaction(
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
        pgTransactionRepository.create(COMPLETEDTransaction)
        pgTransactionRepository.create(pendingTransaction)

        // Then
        val results = pgTransactionRepository.getTransactionsByUserId(testUserId)
        assertEquals(2, results.size)
        assertTrue(results.any { it.status == TransactionStatus.COMPLETED })
        assertTrue(results.any { it.status == TransactionStatus.PENDING_CANCEL })
    }
}
