package org.my.firstcircletest.delivery.http.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.domain.entities.*
import org.my.firstcircletest.domain.usecases.CreateUserError
import org.my.firstcircletest.domain.usecases.CreateUserUseCase
import org.my.firstcircletest.domain.usecases.GetUserTransactionsError
import org.my.firstcircletest.domain.usecases.GetUserTransactionsUseCase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@WebMvcTest(UserController::class)
class UserControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun createUserUseCase(): CreateUserUseCase = mockk()

        @Bean
        fun getUserTransactionsUseCase(): GetUserTransactionsUseCase = mockk()

        @RestControllerAdvice
        class TestExceptionHandler {
            @ExceptionHandler(jakarta.validation.ConstraintViolationException::class)
            @ResponseStatus(HttpStatus.BAD_REQUEST)
            fun handleConstraintViolation(ex: jakarta.validation.ConstraintViolationException): Map<String, String> {
                return mapOf("error" to "VALIDATION_ERROR", "message" to (ex.message ?: "Validation failed"))
            }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var createUserUseCase: CreateUserUseCase

    @Autowired
    private lateinit var getUserTransactionsUseCase: GetUserTransactionsUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `createUser should return CREATED status with user data on success`() = runTest {
        // Given
        val requestDto = CreateUserRequestDto(name = "John Doe")
        val expectedUser = User(id = "user123", name = "John Doe")
        val createUserRequest = CreateUserRequest(name = "John Doe", initBalance = 0)

        coEvery { createUserUseCase.invoke(createUserRequest) } returns expectedUser.right()

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user_id").value("user123"))
            .andExpect(jsonPath("$.name").value("John Doe"))
    }

    @Test
    fun `createUser should return INTERNAL_SERVER_ERROR when user creation fails`() = runTest {
        // Given
        val requestDto = CreateUserRequestDto(name = "John Doe")
        val createUserRequest = CreateUserRequest(name = "John Doe", initBalance = 0)
        val error = CreateUserError.UserCreationFailed("Database error")

        coEvery { createUserUseCase.invoke(createUserRequest) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("USER_CREATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Database error"))
    }

    @Test
    fun `createUser should return INTERNAL_SERVER_ERROR when wallet creation fails`() = runTest {
        // Given
        val requestDto = CreateUserRequestDto(name = "John Doe")
        val createUserRequest = CreateUserRequest(name = "John Doe", initBalance = 0)
        val error = CreateUserError.WalletCreationFailed("Wallet service unavailable")

        coEvery { createUserUseCase.invoke(createUserRequest) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("WALLET_CREATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Wallet service unavailable"))
    }

    @Test
    fun `createUser should return BAD_REQUEST when name is blank`() {
        // Given
        val requestDto = CreateUserRequestDto(name = "")

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
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
        mockMvc.perform(get("/api/users/$userId/transactions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(userId))
            .andExpect(jsonPath("$.transactions").isArray)
            .andExpect(jsonPath("$.transactions.length()").value(2))
            .andExpect(jsonPath("$.transactions[0].transaction_id").value("tx1"))
            .andExpect(jsonPath("$.transactions[1].transaction_id").value("tx2"))
    }

    @Test
    fun `getUserTransactions should return BAD_REQUEST when userId is blank`() {
        // When & Then
        mockMvc.perform(get("/api/users/ /transactions"))
            .andExpect(status().isBadRequest) // Spring validation catches blank userId
    }

    @Test
    fun `getUserTransactions should return BAD_REQUEST for invalid user id error`() = runTest {
        // Given
        val userId = "invalidUser"
        val error = GetUserTransactionsError.InvalidUserId("Invalid user ID format")

        coEvery { getUserTransactionsUseCase.invoke(userId) } returns error.left()

        // When & Then
        mockMvc.perform(get("/api/users/$userId/transactions"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_USER_ID"))
            .andExpect(jsonPath("$.message").value("Invalid user ID format"))
    }

    @Test
    fun `getUserTransactions should return INTERNAL_SERVER_ERROR when transaction retrieval fails`() = runTest {
        // Given
        val userId = "user123"
        val error = GetUserTransactionsError.TransactionRetrievalFailed("Database connection error")

        coEvery { getUserTransactionsUseCase.invoke(userId) } returns error.left()

        // When & Then
        mockMvc.perform(get("/api/users/$userId/transactions"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("TRANSACTION_RETRIEVAL_FAILED"))
            .andExpect(jsonPath("$.message").value("Database connection error"))
    }
}
