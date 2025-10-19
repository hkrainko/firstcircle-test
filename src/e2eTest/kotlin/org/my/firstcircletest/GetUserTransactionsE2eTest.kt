package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.TransactionReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.UserReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.WalletReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.entities.TransactionEntity
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.delivery.http.dto.response.GetUserTransactionsResponseDto
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class GetUserTransactionsE2eTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userReactiveRepository: UserReactiveRepository

    @Autowired
    private lateinit var walletReactiveRepository: WalletReactiveRepository

    @Autowired
    private lateinit var transactionReactiveRepository: TransactionReactiveRepository

    private lateinit var testUserId: String
    private lateinit var testWalletId: String

    @BeforeEach
    fun setUp() = runBlocking {
        // Clean up database before each test
        transactionReactiveRepository.deleteAll()
        walletReactiveRepository.deleteAll()
        userReactiveRepository.deleteAll()

        // Create a test user with initial balance
        val createUserRequest = CreateUserRequestDto(
            name = "Test User",
            initBalance = 1000
        )

        val response = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createUserRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(CreateUserResponseDto::class.java)
            .returnResult()
            .responseBody!!

        testUserId = response.userId

        // Get the wallet ID for the created user
        val wallet = walletReactiveRepository.findByUserId(testUserId)
        assertNotNull(wallet)
        testWalletId = wallet!!.id
    }

    @Test
    fun `should return empty transactions list for user with no transactions`() {
        // When & Then
        webTestClient.get()
            .uri("/api/users/$testUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(testUserId)
            .jsonPath("$.transactions").isArray
            .jsonPath("$.transactions").isEmpty
    }

    @Test
    fun `should return transactions after creating deposits`() = runBlocking {
        // Given - Create deposit transactions
        createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.DEPOSIT,
            amount = 500,
            status = TransactionStatus.COMPLETED
        )

        createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.DEPOSIT,
            amount = 300,
            status = TransactionStatus.COMPLETED
        )

        // When
        val responseBody = webTestClient.get()
            .uri("/api/users/$testUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody(GetUserTransactionsResponseDto::class.java)
            .returnResult()
            .responseBody!!

        // Then
        assertEquals(testUserId, responseBody.userId)
        assertEquals(2, responseBody.transactions.size)
        assertTrue(responseBody.transactions.all { it.type == "DEPOSIT" })
        assertTrue(responseBody.transactions.any { it.amount == 500L })
        assertTrue(responseBody.transactions.any { it.amount == 300L })
    }

    @Test
    fun `should return transactions of different types`() = runBlocking {
        // Given - Create different transaction types
        createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.DEPOSIT,
            amount = 500,
            status = TransactionStatus.COMPLETED
        )

        createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.WITHDRAWAL,
            amount = 200,
            status = TransactionStatus.COMPLETED
        )

        createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.DEPOSIT,
            amount = 100,
            status = TransactionStatus.PENDING_CANCEL
        )

        // When
        val response = webTestClient.get()
            .uri("/api/users/$testUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody(GetUserTransactionsResponseDto::class.java)
            .returnResult()
            .responseBody!!

        // Then
        assertEquals(testUserId, response.userId)
        assertEquals(3, response.transactions.size)
        assertEquals(2, response.transactions.count { it.type == "DEPOSIT" })
        assertEquals(1, response.transactions.count { it.type == "WITHDRAWAL" })
        assertEquals(2, response.transactions.count { it.state == "COMPLETED" })
        assertEquals(1, response.transactions.count { it.state == "PENDING_CANCEL" })
    }

    @Test
    fun `should return transactions in descending order by created date`() = runBlocking {
        // Given - Create transactions with delays
        val transaction1 = createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.DEPOSIT,
            amount = 100,
            status = TransactionStatus.COMPLETED,
            createdAt = LocalDateTime.now().minusHours(2)
        )

        val transaction2 = createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.DEPOSIT,
            amount = 200,
            status = TransactionStatus.COMPLETED,
            createdAt = LocalDateTime.now().minusHours(1)
        )

        val transaction3 = createTransaction(
            walletId = testWalletId,
            userId = testUserId,
            type = TransactionType.WITHDRAWAL,
            amount = 50,
            status = TransactionStatus.COMPLETED,
            createdAt = LocalDateTime.now()
        )

        // When
        val response = webTestClient.get()
            .uri("/api/users/$testUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody(GetUserTransactionsResponseDto::class.java)
            .returnResult()
            .responseBody!!

        // Then
        assertEquals(3, response.transactions.size)
        // Most recent transaction should be first
        assertEquals(transaction3.id, response.transactions[0].transactionId)
        assertEquals(transaction2.id, response.transactions[1].transactionId)
        assertEquals(transaction1.id, response.transactions[2].transactionId)
    }

    @Test
    fun `should return empty transactions for non-existent user ID`() {
        // Given - A user ID that doesn't exist in the database but has valid format
        val nonExistentUserId = "user-00000000-0000-0000-0000-000000000000"

        // When & Then
        webTestClient.get()
            .uri("/api/users/$nonExistentUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(nonExistentUserId)
            .jsonPath("$.transactions").isArray
            .jsonPath("$.transactions").isEmpty
    }

    @Test
    fun `should return empty list for valid user ID with no transactions`() {
        // Given - Create another user without transactions
        val createUserRequest = CreateUserRequestDto(
            name = "Another User",
            initBalance = 500
        )

        val newUser = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createUserRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(CreateUserResponseDto::class.java)
            .returnResult()
            .responseBody!!

        // When & Then
        webTestClient.get()
            .uri("/api/users/${newUser.userId}/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(newUser.userId)
            .jsonPath("$.transactions").isArray
            .jsonPath("$.transactions").isEmpty
    }

    // Helper methods

    private suspend fun createTransaction(
        walletId: String,
        userId: String,
        type: TransactionType,
        amount: Long,
        status: TransactionStatus,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): TransactionEntity {
        val transaction = TransactionEntity.newTransactionEntity(
            walletId = walletId,
            userId = userId,
            destinationWalletId = null,
            destinationUserId = null,
            amount = amount,
            type = type,
            status = status,
            createdAt = createdAt,
        )
        return transactionReactiveRepository.save(transaction)
    }
}
