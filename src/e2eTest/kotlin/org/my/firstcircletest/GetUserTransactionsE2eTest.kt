package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.TransactionJpaRepository
import org.my.firstcircletest.data.repositories.postgres.UserJpaRepository
import org.my.firstcircletest.data.repositories.postgres.WalletJpaRepository
import org.my.firstcircletest.data.repositories.postgres.entities.TransactionEntity
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.delivery.http.dto.response.GetUserTransactionsResponseDto
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class GetUserTransactionsE2eTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var walletJpaRepository: WalletJpaRepository

    @Autowired
    private lateinit var transactionJpaRepository: TransactionJpaRepository

    private lateinit var testUserId: String
    private lateinit var testWalletId: String

    @BeforeEach
    fun setUp() {
        // Clean up database before each test
        transactionJpaRepository.deleteAll()
        walletJpaRepository.deleteAll()
        userJpaRepository.deleteAll()

        // Create a test user with initial balance
        val createUserRequest = CreateUserRequestDto(
            name = "Test User",
            initBalance = 1000
        )

        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val response = objectMapper.readValue(
            result.response.contentAsString,
            CreateUserResponseDto::class.java
        )

        testUserId = response.userId

        // Get the wallet ID for the created user
        val wallet = walletJpaRepository.findByUserId(testUserId)
        assertTrue(wallet.isPresent)
        testWalletId = wallet.get().id
    }

    @Test
    fun `should return empty transactions list for user with no transactions`() {
        // When
        val result = mockMvc.perform(
            get("/api/users/$testUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(testUserId))
            .andExpect(jsonPath("$.transactions").isArray)
            .andExpect(jsonPath("$.transactions").isEmpty)
    }

    @Test
    fun `should return transactions after creating deposits`() {
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
        val result = mockMvc.perform(
            get("/api/users/$testUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(testUserId))
            .andExpect(jsonPath("$.transactions").isArray)
            .andExpect(jsonPath("$.transactions.length()").value(2))
            .andExpect(jsonPath("$.transactions[0].type").value("DEPOSIT"))
            .andExpect(jsonPath("$.transactions[1].type").value("DEPOSIT"))

        val responseJson = result.andReturn().response.contentAsString
        val response = objectMapper.readValue(responseJson, GetUserTransactionsResponseDto::class.java)

        assertEquals(2, response.transactions.size)
        assertTrue(response.transactions.any { it.amount == 500 })
        assertTrue(response.transactions.any { it.amount == 300 })
    }

    @Test
    fun `should return transactions of different types`() {
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
        val result = mockMvc.perform(
            get("/api/users/$testUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(testUserId))
            .andExpect(jsonPath("$.transactions.length()").value(3))

        val responseJson = result.andReturn().response.contentAsString
        val response = objectMapper.readValue(responseJson, GetUserTransactionsResponseDto::class.java)

        assertEquals(3, response.transactions.size)
        assertEquals(2, response.transactions.count { it.type == "DEPOSIT" })
        assertEquals(1, response.transactions.count { it.type == "WITHDRAWAL" })
        assertEquals(2, response.transactions.count { it.state == "COMPLETED" })
        assertEquals(1, response.transactions.count { it.state == "PENDING_CANCEL" })
    }

    @Test
    fun `should return transactions in descending order by created date`() {
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
        val result = mockMvc.perform(
            get("/api/users/$testUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )

        // Then
        val responseJson = result.andReturn().response.contentAsString
        val response = objectMapper.readValue(responseJson, GetUserTransactionsResponseDto::class.java)

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
        mockMvc.perform(
            get("/api/users/$nonExistentUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(nonExistentUserId))
            .andExpect(jsonPath("$.transactions").isArray)
            .andExpect(jsonPath("$.transactions").isEmpty)
    }

    @Test
    fun `should return empty list for valid user ID with no transactions`() {
        // Given - Create another user without transactions
        val createUserRequest = CreateUserRequestDto(
            name = "Another User",
            initBalance = 500
        )

        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val newUser = objectMapper.readValue(
            result.response.contentAsString,
            CreateUserResponseDto::class.java
        )

        // When
        val transactionsResult = mockMvc.perform(
            get("/api/users/${newUser.userId}/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )

        // Then
        transactionsResult
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(newUser.userId))
            .andExpect(jsonPath("$.transactions").isArray)
            .andExpect(jsonPath("$.transactions").isEmpty)
    }

    // Helper methods

    private fun createTransaction(
        walletId: String,
        userId: String,
        type: TransactionType,
        amount: Int,
        status: TransactionStatus,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): TransactionEntity {
        val transaction = TransactionEntity(
            id = "tx-${UUID.randomUUID()}",
            walletId = walletId,
            userId = userId,
            amount = amount,
            type = type.name,
            status = status.name,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        return transactionJpaRepository.save(transaction)
    }

    private fun createTransferTransaction(
        walletId: String,
        userId: String,
        destinationWalletId: String,
        destinationUserId: String,
        amount: Int,
        status: TransactionStatus,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): TransactionEntity {
        val transaction = TransactionEntity(
            id = "tx-${UUID.randomUUID()}",
            walletId = walletId,
            userId = userId,
            destinationWalletId = destinationWalletId,
            destinationUserId = destinationUserId,
            amount = amount,
            type = TransactionType.TRANSFER.name,
            status = status.name,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        return transactionJpaRepository.save(transaction)
    }
}
