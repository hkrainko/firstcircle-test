package org.my.firstcircletest.data.repositories.postgres

import arrow.core.getOrElse
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitSingleOrNull
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class PgWalletRepositoryIntegrationTest {

    @Autowired
    private lateinit var pgWalletRepository: PgWalletRepository

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private lateinit var testUserId: String

    @BeforeEach
    fun setUp() = runTest {
        testUserId = "user-${UUID.randomUUID()}"

        // Create test user
        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, testUserId)
            .bind(1, "Test User")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()
    }

    @Test
    fun `createWallet should persist wallet successfully`() = runTest {
        // Given
        val request = CreateWalletRequest(userId = testUserId, balance = 100000)

        // When
        val eitherResult = pgWalletRepository.createWallet(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertNotNull(result.id)
        assertTrue(result.id.toString().startsWith("wallet-"))
        assertEquals(testUserId, result.userId)
        assertEquals(100000, result.balance)

        // Verify it was persisted
        val found = databaseClient.sql("SELECT * FROM wallets WHERE id = $1")
            .bind(0, result.id)
            .fetch()
            .awaitSingleOrNull()
        assertNotNull(found)
        assertEquals(testUserId, found?.get("user_id"))
        assertEquals(100000L, found?.get("balance"))
    }

    @Test
    fun `createWallet should create wallet with zero balance`() = runTest {
        // Given
        val request = CreateWalletRequest(userId = testUserId, balance = 0)

        // When
        val eitherResult = pgWalletRepository.createWallet(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(0, result.balance)
    }

    @Test
    fun `createWallet should create wallet with negative balance`() = runTest {
        // Given
        val request = CreateWalletRequest(userId = testUserId, balance = -1000)

        // When
        val eitherResult = pgWalletRepository.createWallet(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(-1000, result.balance)
    }

    @Test
    fun `createWallet should generate unique IDs for different wallets`() = runTest {
        // Given
        val userId1 = "user-${UUID.randomUUID()}"
        val userId2 = "user-${UUID.randomUUID()}"

        // Create users
        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, userId1)
            .bind(1, "User One")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, userId2)
            .bind(1, "User Two")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        val request1 = CreateWalletRequest(userId = userId1, balance = 10000)
        val request2 = CreateWalletRequest(userId = userId2, balance = 20000)

        // When
        val eitherResult1 = pgWalletRepository.createWallet(request1)
        val eitherResult2 = pgWalletRepository.createWallet(request2)

        // Then
        assertTrue(eitherResult1.isRight())
        assertTrue(eitherResult2.isRight())
        val result1 = eitherResult1.getOrElse { fail("Expected Right but got Left") }
        val result2 = eitherResult2.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result1.id)
        assertNotNull(result2.id)
        assertNotEquals(result1.id, result2.id)
        assertTrue(result1.id.toString().startsWith("wallet-"))
        assertTrue(result2.id.toString().startsWith("wallet-"))
        assertEquals(userId1, result1.userId)
        assertEquals(userId2, result2.userId)
    }

    @Test
    fun `getWalletByUserId should return wallet when it exists`() = runTest {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId)
            .bind(1, testUserId)
            .bind(2, 75000)
            .fetch().rowsUpdated().awaitSingle()

        // When
        val eitherResult = pgWalletRepository.getWalletByUserId(testUserId)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(walletId, result.id)
        assertEquals(testUserId, result.userId)
        assertEquals(75000, result.balance)
    }

    @Test
    fun `getWalletByUserId should return Left with NotFound when wallet does not exist`() = runTest {
        // Given
        val nonExistentUserId = "user-${UUID.randomUUID()}"

        // When
        val eitherResult = pgWalletRepository.getWalletByUserId(nonExistentUserId)

        // Then
        assertTrue(eitherResult.isLeft())
        eitherResult.onLeft { error ->
            assertTrue(error is RepositoryError.NotFound)
            assertTrue(error.message.contains("Wallet not found for user"))
        }
    }

    @Test
    fun `updateWalletBalance should update balance successfully`() = runTest {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId)
            .bind(1, testUserId)
            .bind(2, 50000)
            .fetch().rowsUpdated().awaitSingle()

        // When
        val eitherResult = pgWalletRepository.updateWalletBalance(walletId, 75000)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(walletId, result.id)
        assertEquals(75000, result.balance)

        // Verify it was updated in database
        val found = databaseClient.sql("SELECT * FROM wallets WHERE id = $1")
            .bind(0, walletId)
            .fetch()
            .awaitSingleOrNull()
        assertEquals(75000L, found?.get("balance"))
    }

    @Test
    fun `updateWalletBalance should update balance to zero`() = runTest {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId)
            .bind(1, testUserId)
            .bind(2, 50000)
            .fetch().rowsUpdated().awaitSingle()

        // When
        val eitherResult = pgWalletRepository.updateWalletBalance(walletId, 0)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertEquals(0, result.balance)
    }

    @Test
    fun `updateWalletBalance should update balance to negative value`() = runTest {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId)
            .bind(1, testUserId)
            .bind(2, 50000)
            .fetch().rowsUpdated().awaitSingle()

        // When
        val eitherResult = pgWalletRepository.updateWalletBalance(walletId, -5000)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertEquals(-5000, result.balance)
    }

    @Test
    fun `updateWalletBalance should return Left with NotFound when wallet does not exist`() = runTest {
        // Given
        val nonExistentWalletId = "wallet-${UUID.randomUUID()}"

        // When
        val eitherResult = pgWalletRepository.updateWalletBalance(nonExistentWalletId, 100000)

        // Then
        assertTrue(eitherResult.isLeft())
        eitherResult.onLeft { error ->
            assertTrue(error is RepositoryError.NotFound)
            assertTrue(error.message.contains("Wallet not found"))
        }
    }

    @Test
    fun `updateWalletBalance should handle multiple updates correctly`() = runTest {
        // Given
        val walletId = "wallet-${UUID.randomUUID()}"
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId)
            .bind(1, testUserId)
            .bind(2, 10000)
            .fetch().rowsUpdated().awaitSingle()

        // When
        val eitherResult1 = pgWalletRepository.updateWalletBalance(walletId, 20000)
        val eitherResult2 = pgWalletRepository.updateWalletBalance(walletId, 30000)
        val eitherResult3 = pgWalletRepository.updateWalletBalance(walletId, 15000)

        // Then
        assertTrue(eitherResult1.isRight())
        assertTrue(eitherResult2.isRight())
        assertTrue(eitherResult3.isRight())
        val result1 = eitherResult1.getOrElse { fail("Expected Right but got Left") }
        val result2 = eitherResult2.getOrElse { fail("Expected Right but got Left") }
        val result3 = eitherResult3.getOrElse { fail("Expected Right but got Left") }
        assertEquals(20000, result1.balance)
        assertEquals(30000, result2.balance)
        assertEquals(15000, result3.balance)

        // Verify final state
        val found = databaseClient.sql("SELECT * FROM wallets WHERE id = $1")
            .bind(0, walletId)
            .fetch()
            .awaitSingleOrNull()
        assertEquals(15000L, found?.get("balance"))
    }

    @Test
    fun `getWalletByUserId should return correct wallet when multiple wallets exist`() = runTest {
        // Given
        val userId1 = "user-${UUID.randomUUID()}"
        val userId2 = "user-${UUID.randomUUID()}"
        val walletId1 = UUID.randomUUID().toString()
        val walletId2 = UUID.randomUUID().toString()

        // Create users
        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, userId1)
            .bind(1, "User One")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO users (id, name, created_at) VALUES ($1, $2, $3)")
            .bind(0, userId2)
            .bind(1, "User Two")
            .bind(2, LocalDateTime.now())
            .fetch().rowsUpdated().awaitSingle()

        // Create wallets
        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId1)
            .bind(1, userId1)
            .bind(2, 10000)
            .fetch().rowsUpdated().awaitSingle()

        databaseClient.sql("INSERT INTO wallets (id, user_id, balance) VALUES ($1, $2, $3)")
            .bind(0, walletId2)
            .bind(1, userId2)
            .bind(2, 20000)
            .fetch().rowsUpdated().awaitSingle()

        // When
        val eitherResult1 = pgWalletRepository.getWalletByUserId(userId1)
        val eitherResult2 = pgWalletRepository.getWalletByUserId(userId2)

        // Then
        assertTrue(eitherResult1.isRight())
        assertTrue(eitherResult2.isRight())
        val result1 = eitherResult1.getOrElse { fail("Expected Right but got Left") }
        val result2 = eitherResult2.getOrElse { fail("Expected Right but got Left") }
        assertEquals(walletId1, result1.id)
        assertEquals(10000, result1.balance)
        assertEquals(walletId2, result2.id)
        assertEquals(20000, result2.balance)
    }
}
