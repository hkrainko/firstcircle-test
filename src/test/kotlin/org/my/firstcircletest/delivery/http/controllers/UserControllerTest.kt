package org.my.firstcircletest.delivery.http.controllers

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.domain.entities.*
import org.my.firstcircletest.domain.usecases.CreateUserError
import org.my.firstcircletest.domain.usecases.CreateUserUseCase
import org.my.firstcircletest.domain.usecases.GetUserTransactionsError
import org.my.firstcircletest.domain.usecases.GetUserTransactionsUseCase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDateTime

@WebFluxTest
@ContextConfiguration(classes = [UserControllerTest.TestConfig::class, UserController::class])
class UserControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun createUserUseCase(): CreateUserUseCase = mockk()

        @Bean
        fun getUserTransactionsUseCase(): GetUserTransactionsUseCase = mockk()
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var createUserUseCase: CreateUserUseCase

    @Autowired
    private lateinit var getUserTransactionsUseCase: GetUserTransactionsUseCase

    @Test
    fun `createUser should return CREATED status with user data on success`() = runTest {
        // Given
        val requestDto = CreateUserRequestDto(name = "John Doe")
        val expectedUser = User(id = "user123", name = "John Doe")
        val createUserRequest = CreateUserRequest(name = "John Doe", initBalance = 0)

        coEvery { createUserUseCase.invoke(createUserRequest) } returns expectedUser.right()

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.user_id").isEqualTo("user123")
            .jsonPath("$.name").isEqualTo("John Doe")
    }

    @Test
    fun `createUser should return INTERNAL_SERVER_ERROR when user creation fails`() = runTest {
        // Given
        val requestDto = CreateUserRequestDto(name = "John Doe")
        val createUserRequest = CreateUserRequest(name = "John Doe", initBalance = 0)
        val error = CreateUserError.UserCreationFailed("Database error")

        coEvery { createUserUseCase.invoke(createUserRequest) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.error").isEqualTo("USER_CREATION_FAILED")
            .jsonPath("$.message").isEqualTo("Database error")
    }

    @Test
    fun `createUser should return INTERNAL_SERVER_ERROR when wallet creation fails`() = runTest {
        // Given
        val requestDto = CreateUserRequestDto(name = "John Doe")
        val createUserRequest = CreateUserRequest(name = "John Doe", initBalance = 0)
        val error = CreateUserError.WalletCreationFailed("Wallet service unavailable")

        coEvery { createUserUseCase.invoke(createUserRequest) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_CREATION_FAILED")
            .jsonPath("$.message").isEqualTo("Wallet service unavailable")
    }

    @Test
    fun `createUser should return BAD_REQUEST when name is blank`() {
        // Given
        val requestDto = CreateUserRequestDto(name = "")

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `getUserTransactions should return OK with transactions on success`() = runTest {
        // Given
        val userId = "user123"
        val walletId = "wallet123"
        val transactions = listOf(
            Transaction(
                id = "tx1",
                walletId = walletId,
                userId = userId,
                amount = 100,
                type = TransactionType.DEPOSIT,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                status = TransactionStatus.COMPLETED
            ),
            Transaction(
                id = "tx2",
                walletId = walletId,
                userId = userId,
                amount = 50,
                type = TransactionType.WITHDRAWAL,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                status = TransactionStatus.COMPLETED
            )
        )

        coEvery { getUserTransactionsUseCase.invoke(userId) } returns transactions.right()

        // When & Then
        webTestClient.get()
            .uri("/api/users/$userId/transactions")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(userId)
            .jsonPath("$.transactions").isArray
            .jsonPath("$.transactions.length()").isEqualTo(2)
            .jsonPath("$.transactions[0].transaction_id").isEqualTo("tx1")
            .jsonPath("$.transactions[1].transaction_id").isEqualTo("tx2")
    }

    @Test
    fun `getUserTransactions should return BAD_REQUEST for invalid user id error`() = runTest {
        // Given
        val userId = "invalidUser"
        val error = GetUserTransactionsError.InvalidUserId("Invalid user ID format")

        coEvery { getUserTransactionsUseCase.invoke(userId) } returns error.left()

        // When & Then
        webTestClient.get()
            .uri("/api/users/$userId/transactions")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_USER_ID")
            .jsonPath("$.message").isEqualTo("Invalid user ID format")
    }

    @Test
    fun `getUserTransactions should return INTERNAL_SERVER_ERROR when transaction retrieval fails`() = runTest {
        // Given
        val userId = "user123"
        val error = GetUserTransactionsError.TransactionRetrievalFailed("Database connection error")

        coEvery { getUserTransactionsUseCase.invoke(userId) } returns error.left()

        // When & Then
        webTestClient.get()
            .uri("/api/users/$userId/transactions")
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.error").isEqualTo("TRANSACTION_RETRIEVAL_FAILED")
            .jsonPath("$.message").isEqualTo("Database connection error")
    }
}
