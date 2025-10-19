package org.my.firstcircletest.delivery.http.controllers

import arrow.core.left
import arrow.core.right
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.my.firstcircletest.delivery.http.dto.request.DepositRequestDto
import org.my.firstcircletest.delivery.http.dto.request.TransferRequestDto
import org.my.firstcircletest.delivery.http.dto.request.WithdrawRequestDto
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.usecases.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest
@ContextConfiguration(classes = [WalletControllerTest.TestConfig::class, WalletController::class])
class WalletControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun getWalletInfoUseCase(): GetWalletInfoUseCase = mockk()

        @Bean
        fun depositUseCase(): DepositUseCase = mockk()

        @Bean
        fun withdrawUseCase(): WithdrawUseCase = mockk()

        @Bean
        fun transferUseCase(): TransferUseCase = mockk()
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var getWalletInfoUseCase: GetWalletInfoUseCase

    @Autowired
    private lateinit var depositUseCase: DepositUseCase

    @Autowired
    private lateinit var withdrawUseCase: WithdrawUseCase

    @Autowired
    private lateinit var transferUseCase: TransferUseCase

    // GetWalletInfo Tests
    @Test
    fun `getWalletInfo should return OK with wallet data on success`() = runTest {
        // Given
        val walletId = "wallet123"
        val userId = "user123"
        val wallet = Wallet(id = walletId, userId = userId, balance = 1000)

        coEvery { getWalletInfoUseCase.invoke(userId) } returns wallet.right()

        // When & Then
        webTestClient.get()
            .uri("/api/users/$userId/wallet")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(userId)
            .jsonPath("$.balance").isEqualTo(1000)
    }

    @Test
    fun `getWalletInfo should return BAD_REQUEST for invalid user id`() = runTest {
        // Given
        val userId = "invalidUser"
        val error = GetWalletInfoError.InvalidUserId("Invalid user ID format")

        coEvery { getWalletInfoUseCase.invoke(userId) } returns error.left()

        // When & Then
        webTestClient.get()
            .uri("/api/users/$userId/wallet")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_USER_ID")
            .jsonPath("$.message").isEqualTo("Invalid user ID format")
    }

    @Test
    fun `getWalletInfo should return NOT_FOUND when wallet not found`() = runTest {
        // Given
        val userId = "user123"
        val error = GetWalletInfoError.WalletNotFound("Wallet not found for user")

        coEvery { getWalletInfoUseCase.invoke(userId) } returns error.left()

        // When & Then
        webTestClient.get()
            .uri("/api/users/$userId/wallet")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")
            .jsonPath("$.message").isEqualTo("Wallet not found for user")
    }

    // Deposit Tests
    @Test
    fun `deposit should return OK with updated wallet on success`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500L
        val requestDto = DepositRequestDto(amount = amount)
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1500)

        coEvery { depositUseCase.invoke(userId, amount) } returns wallet.right()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$userId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(userId)
            .jsonPath("$.amount").isEqualTo(1500)
    }

    @Test
    fun `deposit should return BAD_REQUEST for invalid user id`() = runTest {
        // Given
        val userId = "invalidUser"
        val amount = 500L
        val requestDto = DepositRequestDto(amount = amount)
        val error = DepositError.InvalidUserId("Invalid user ID format")

        coEvery { depositUseCase.invoke(userId, amount) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$userId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_USER_ID")
    }

    @Test
    fun `deposit should return BAD_REQUEST for non-positive amount`() = runTest {
        // Given
        val userId = "user123"
        val amount = -100L
        val requestDto = DepositRequestDto(amount = amount)

        // When & Then
        // Note: Validation happens at DTO level before controller logic is invoked
        webTestClient.post()
            .uri("/api/users/$userId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `deposit should return NOT_FOUND when wallet not found`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500L
        val requestDto = DepositRequestDto(amount = amount)
        val error = DepositError.WalletNotFound("Wallet not found")

        coEvery { depositUseCase.invoke(userId, amount) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$userId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")
    }

    // Withdraw Tests
    @Test
    fun `withdraw should return OK with updated wallet on success`() = runTest {
        // Given
        val userId = "user123"
        val amount = 300L
        val requestDto = WithdrawRequestDto(amount = amount)
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 700)

        coEvery { withdrawUseCase.invoke(userId, amount) } returns wallet.right()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$userId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.user_id").isEqualTo(userId)
            .jsonPath("$.balance").isEqualTo(700)
    }

    @Test
    fun `withdraw should return BAD_REQUEST for insufficient balance`() = runTest {
        // Given
        val userId = "user123"
        val amount = 2000L
        val requestDto = WithdrawRequestDto(amount = amount)
        val error = WithdrawError.InsufficientBalance("Insufficient balance")

        coEvery { withdrawUseCase.invoke(userId, amount) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$userId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INSUFFICIENT_BALANCE")
    }

    @Test
    fun `withdraw should return BAD_REQUEST for non-positive amount`() = runTest {
        // Given
        val userId = "user123"
        val amount = 0L
        val requestDto = WithdrawRequestDto(amount = amount)

        // When & Then
        // Note: Validation happens at DTO level before controller logic is invoked
        webTestClient.post()
            .uri("/api/users/$userId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
    }

    // Transfer Tests
    @Test
    fun `transfer should return OK with transfer details on success`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 200L
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)

        coEvery { transferUseCase.invoke(transfer) } returns transfer.right()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$fromUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.from_user_id").isEqualTo(fromUserId)
            .jsonPath("$.to_user_id").isEqualTo(toUserId)
            .jsonPath("$.amount").isEqualTo(200)
    }

    @Test
    fun `transfer should return BAD_REQUEST for same user transfer`() = runTest {
        // Given
        val userId = "user123"
        val amount = 200L
        val requestDto = TransferRequestDto(toUserId = userId, amount = amount)
        val transfer = Transfer(fromUserId = userId, toUserId = userId, amount = amount)
        val error = TransferError.SameUserTransfer("Cannot transfer to yourself")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$userId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("SAME_USER_TRANSFER")
    }

    @Test
    fun `transfer should return BAD_REQUEST for insufficient balance`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 5000L
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)
        val error = TransferError.InsufficientBalance("Insufficient balance")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$fromUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INSUFFICIENT_BALANCE")
    }

    @Test
    fun `transfer should return NOT_FOUND when wallet not found`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "nonexistent"
        val amount = 200L
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)
        val error = TransferError.WalletNotFound("Target wallet not found")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$fromUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")
    }

    @Test
    fun `transfer should return INTERNAL_SERVER_ERROR when wallet update fails`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 200L
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)
        val error = TransferError.WalletUpdateFailed("Database error during transfer")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        webTestClient.post()
            .uri("/api/users/$fromUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_UPDATE_FAILED")
    }
}
