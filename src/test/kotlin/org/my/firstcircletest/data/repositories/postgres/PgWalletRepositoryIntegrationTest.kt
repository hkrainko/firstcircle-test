package org.my.firstcircletest.data.repositories.postgres

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@DataJpaTest
@ActiveProfiles("test")
@Import(PgWalletRepository::class)
class PgWalletRepositoryIntegrationTest {

    @Autowired
    private lateinit var pgWalletRepository: PgWalletRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var testUserId: String

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"

        // Create test user
        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, testUserId.toString())
            setParameter(2, "Test User")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        entityManager.flush()
    }

    @Test
    fun `createWallet should persist wallet successfully`() {
        // Given
        val request = CreateWalletRequest(userId = testUserId, balance = 100000)

        // When
        val result = pgWalletRepository.createWallet(request)

        // Then
        assertNotNull(result)
        assertNotNull(result.id)
        assertTrue(result.id.toString().startsWith("wallet-"))
        assertEquals(testUserId, result.userId)
        assertEquals(100000, result.balance)

        // Verify it was persisted
        val found = entityManager.find(
            org.my.firstcircletest.data.repositories.postgres.dto.WalletDTO::class.java,
            result.id.toString()
        )
        assertNotNull(found)
        assertEquals(testUserId.toString(), found.userId)
        assertEquals(100000, found.balance)
    }

    @Test
    fun `createWallet should create wallet with zero balance`() {
        // Given
        val request = CreateWalletRequest(userId = testUserId, balance = 0)

        // When
        val result = pgWalletRepository.createWallet(request)

        // Then
        assertNotNull(result)
        assertEquals(0, result.balance)
    }

    @Test
    fun `createWallet should create wallet with negative balance`() {
        // Given
        val request = CreateWalletRequest(userId = testUserId, balance = -1000)

        // When
        val result = pgWalletRepository.createWallet(request)

        // Then
        assertNotNull(result)
        assertEquals(-1000, result.balance)
    }

    @Test
    fun `createWallet should generate unique IDs for different wallets`() {
        // Given
        val userId1 = "user-${UUID.randomUUID()}"
        val userId2 = "user-${UUID.randomUUID()}"

        // Create users
        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, userId1.toString())
            setParameter(2, "User One")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, userId2.toString())
            setParameter(2, "User Two")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()
        entityManager.flush()

        val request1 = CreateWalletRequest(userId = userId1, balance = 10000)
        val request2 = CreateWalletRequest(userId = userId2, balance = 20000)

        // When
        val result1 = pgWalletRepository.createWallet(request1)
        val result2 = pgWalletRepository.createWallet(request2)

        // Then
        assertNotNull(result1.id)
        assertNotNull(result2.id)
        assertNotEquals(result1.id, result2.id)
        assertTrue(result1.id.toString().startsWith("wallet-"))
        assertTrue(result2.id.toString().startsWith("wallet-"))
        assertEquals(userId1, result1.userId)
        assertEquals(userId2, result2.userId)
    }

    @Test
    fun `getWalletByUserId should return wallet when it exists`() {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId)
            setParameter(2, testUserId.toString())
            setParameter(3, 75000)
        }.executeUpdate()
        entityManager.flush()

        // When
        val result = pgWalletRepository.getWalletByUserId(testUserId)

        // Then
        assertNotNull(result)
        assertEquals(walletId, result.id)
        assertEquals(testUserId.toString(), result.userId)
        assertEquals(75000, result.balance)
    }

    @Test
    fun `getWalletByUserId should throw WalletNotFoundException when wallet does not exist`() {
        // Given
        val nonExistentUserId = "user-${UUID.randomUUID()}"

        // When & Then
        val exception = assertThrows(DomainError.WalletNotFoundException::class.java) {
            pgWalletRepository.getWalletByUserId(nonExistentUserId)
        }

        assertTrue(exception.message!!.contains("Wallet not found for user"))
    }

    @Test
    fun `updateWalletBalance should update balance successfully`() {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId)
            setParameter(2, testUserId.toString())
            setParameter(3, 50000)
        }.executeUpdate()
        entityManager.flush()

        // When
        val result = pgWalletRepository.updateWalletBalance(walletId, 75000)

        // Then
        assertNotNull(result)
        assertEquals(walletId, result.id)
        assertEquals(75000, result.balance)

        // Verify it was updated in database
        entityManager.clear()
        val found = entityManager.find(
            org.my.firstcircletest.data.repositories.postgres.dto.WalletDTO::class.java,
            walletId
        )
        assertEquals(75000, found.balance)
    }

    @Test
    fun `updateWalletBalance should update balance to zero`() {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId)
            setParameter(2, testUserId.toString())
            setParameter(3, 50000)
        }.executeUpdate()
        entityManager.flush()

        // When
        val result = pgWalletRepository.updateWalletBalance(walletId, 0)

        // Then
        assertEquals(0, result.balance)
    }

    @Test
    fun `updateWalletBalance should update balance to negative value`() {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId)
            setParameter(2, testUserId.toString())
            setParameter(3, 50000)
        }.executeUpdate()
        entityManager.flush()

        // When
        val result = pgWalletRepository.updateWalletBalance(walletId, -5000)

        // Then
        assertEquals(-5000, result.balance)
    }

    @Test
    fun `updateWalletBalance should throw WalletNotFoundException when wallet does not exist`() {
        // Given
        val nonExistentWalletId = "wallet-${UUID.randomUUID()}"

        // When & Then
        val exception = assertThrows(DomainError.WalletNotFoundException::class.java) {
            pgWalletRepository.updateWalletBalance(nonExistentWalletId, 100000)
        }

        assertTrue(exception.message!!.contains("Wallet not found"))
    }

    @Test
    fun `updateWalletBalance should handle multiple updates correctly`() {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId)
            setParameter(2, testUserId.toString())
            setParameter(3, 10000)
        }.executeUpdate()
        entityManager.flush()

        // When
        val result1 = pgWalletRepository.updateWalletBalance(walletId, 20000)
        val result2 = pgWalletRepository.updateWalletBalance(walletId, 30000)
        val result3 = pgWalletRepository.updateWalletBalance(walletId, 15000)

        // Then
        assertEquals(20000, result1.balance)
        assertEquals(30000, result2.balance)
        assertEquals(15000, result3.balance)

        // Verify final state
        entityManager.clear()
        val found = entityManager.find(
            org.my.firstcircletest.data.repositories.postgres.dto.WalletDTO::class.java,
            walletId
        )
        assertEquals(15000, found.balance)
    }

    @Test
    fun `getWalletByUserId should return correct wallet when multiple wallets exist`() {
        // Given
        val userId1 = "user-${UUID.randomUUID()}"
        val userId2 = "user-${UUID.randomUUID()}"
        val walletId1 = UUID.randomUUID().toString()
        val walletId2 = UUID.randomUUID().toString()

        // Create users
        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, userId1.toString())
            setParameter(2, "User One")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO users (id, name, created_at) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, userId2.toString())
            setParameter(2, "User Two")
            setParameter(3, LocalDateTime.now())
        }.executeUpdate()

        // Create wallets
        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId1)
            setParameter(2, userId1.toString())
            setParameter(3, 10000)
        }.executeUpdate()

        entityManager.createNativeQuery(
            "INSERT INTO wallets (id, user_id, balance) VALUES (?, ?, ?)"
        ).apply {
            setParameter(1, walletId2)
            setParameter(2, userId2.toString())
            setParameter(3, 20000)
        }.executeUpdate()
        entityManager.flush()

        // When
        val result1 = pgWalletRepository.getWalletByUserId(userId1)
        val result2 = pgWalletRepository.getWalletByUserId(userId2)

        // Then
        assertEquals(walletId1, result1.id)
        assertEquals(10000, result1.balance)
        assertEquals(walletId2, result2.id)
        assertEquals(20000, result2.balance)
    }
}
