package org.my.firstcircletest.delivery.http.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.my.firstcircletest.delivery.http.dto.request.DepositRequestDto
import org.my.firstcircletest.delivery.http.dto.request.TransferRequestDto
import org.my.firstcircletest.delivery.http.dto.request.WithdrawRequestDto
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.usecases.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@WebMvcTest(WalletController::class)
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
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var getWalletInfoUseCase: GetWalletInfoUseCase

    @Autowired
    private lateinit var depositUseCase: DepositUseCase

    @Autowired
    private lateinit var withdrawUseCase: WithdrawUseCase

    @Autowired
    private lateinit var transferUseCase: TransferUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // GetWalletInfo Tests
    @Test
    fun `getWalletInfo should return OK with wallet data on success`() = runTest {
        // Given
        val walletId = "wallet123"
        val userId = "user123"
        val wallet = Wallet(id = walletId, userId = userId, balance = 1000)

        coEvery { getWalletInfoUseCase.invoke(userId) } returns wallet.right()

        // When & Then
        mockMvc.perform(get("/api/users/$userId/wallet"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(userId))
            .andExpect(jsonPath("$.balance").value(1000))
    }

    @Test
    fun `getWalletInfo should return BAD_REQUEST for invalid user id`() = runTest {
        // Given
        val userId = "invalidUser"
        val error = GetWalletInfoError.InvalidUserId("Invalid user ID format")

        coEvery { getWalletInfoUseCase.invoke(userId) } returns error.left()

        // When & Then
        mockMvc.perform(get("/api/users/$userId/wallet"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_USER_ID"))
            .andExpect(jsonPath("$.message").value("Invalid user ID format"))
    }

    @Test
    fun `getWalletInfo should return NOT_FOUND when wallet not found`() = runTest {
        // Given
        val userId = "user123"
        val error = GetWalletInfoError.WalletNotFound("Wallet not found for user")

        coEvery { getWalletInfoUseCase.invoke(userId) } returns error.left()

        // When & Then
        mockMvc.perform(get("/api/users/$userId/wallet"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Wallet not found for user"))
    }

    // Deposit Tests
    @Test
    fun `deposit should return OK with updated wallet on success`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500
        val requestDto = DepositRequestDto(amount = amount)
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 1500)

        coEvery { depositUseCase.invoke(userId, amount) } returns wallet.right()

        // When & Then
        mockMvc.perform(
            post("/api/users/$userId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(userId))
            .andExpect(jsonPath("$.amount").value(1500))
    }

    @Test
    fun `deposit should return BAD_REQUEST for invalid user id`() = runTest {
        // Given
        val userId = "invalidUser"
        val amount = 500
        val requestDto = DepositRequestDto(amount = amount)
        val error = DepositError.InvalidUserId("Invalid user ID format")

        coEvery { depositUseCase.invoke(userId, amount) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$userId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_USER_ID"))
    }

    @Test
    fun `deposit should return BAD_REQUEST for non-positive amount`() = runTest {
        // Given
        val userId = "user123"
        val amount = -100
        val requestDto = DepositRequestDto(amount = amount)

        // When & Then
        // Note: Validation happens at DTO level before controller logic is invoked
        mockMvc.perform(
            post("/api/users/$userId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `deposit should return NOT_FOUND when wallet not found`() = runTest {
        // Given
        val userId = "user123"
        val amount = 500
        val requestDto = DepositRequestDto(amount = amount)
        val error = DepositError.WalletNotFound("Wallet not found")

        coEvery { depositUseCase.invoke(userId, amount) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$userId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))
    }

    // Withdraw Tests
    @Test
    fun `withdraw should return OK with updated wallet on success`() = runTest {
        // Given
        val userId = "user123"
        val amount = 300
        val requestDto = WithdrawRequestDto(amount = amount)
        val wallet = Wallet(id = "wallet123", userId = userId, balance = 700)

        coEvery { withdrawUseCase.invoke(userId, amount) } returns wallet.right()

        // When & Then
        mockMvc.perform(
            post("/api/users/$userId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user_id").value(userId))
            .andExpect(jsonPath("$.balance").value(700))
    }

    @Test
    fun `withdraw should return BAD_REQUEST for insufficient balance`() = runTest {
        // Given
        val userId = "user123"
        val amount = 2000
        val requestDto = WithdrawRequestDto(amount = amount)
        val error = WithdrawError.InsufficientBalance("Insufficient balance")

        coEvery { withdrawUseCase.invoke(userId, amount) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$userId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"))
    }

    @Test
    fun `withdraw should return BAD_REQUEST for non-positive amount`() = runTest {
        // Given
        val userId = "user123"
        val amount = 0
        val requestDto = WithdrawRequestDto(amount = amount)

        // When & Then
        // Note: Validation happens at DTO level before controller logic is invoked
        mockMvc.perform(
            post("/api/users/$userId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
    }

    // Transfer Tests
    @Test
    fun `transfer should return OK with transfer details on success`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 200
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)

        coEvery { transferUseCase.invoke(transfer) } returns transfer.right()

        // When & Then
        mockMvc.perform(
            post("/api/users/$fromUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from_user_id").value(fromUserId))
            .andExpect(jsonPath("$.to_user_id").value(toUserId))
            .andExpect(jsonPath("$.amount").value(200))
    }

    @Test
    fun `transfer should return BAD_REQUEST for same user transfer`() = runTest {
        // Given
        val userId = "user123"
        val amount = 200
        val requestDto = TransferRequestDto(toUserId = userId, amount = amount)
        val transfer = Transfer(fromUserId = userId, toUserId = userId, amount = amount)
        val error = TransferError.SameUserTransfer("Cannot transfer to yourself")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$userId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("SAME_USER_TRANSFER"))
    }

    @Test
    fun `transfer should return BAD_REQUEST for insufficient balance`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 5000
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)
        val error = TransferError.InsufficientBalance("Insufficient balance")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$fromUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"))
    }

    @Test
    fun `transfer should return NOT_FOUND when wallet not found`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "nonexistent"
        val amount = 200
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)
        val error = TransferError.WalletNotFound("Target wallet not found")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$fromUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))
    }

    @Test
    fun `transfer should return INTERNAL_SERVER_ERROR when wallet update fails`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 200
        val requestDto = TransferRequestDto(toUserId = toUserId, amount = amount)
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)
        val error = TransferError.WalletUpdateFailed("Database error during transfer")

        coEvery { transferUseCase.invoke(transfer) } returns error.left()

        // When & Then
        mockMvc.perform(
            post("/api/users/$fromUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("WALLET_UPDATE_FAILED"))
    }
}
