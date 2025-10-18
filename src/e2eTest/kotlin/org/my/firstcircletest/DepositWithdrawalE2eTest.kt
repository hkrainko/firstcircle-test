package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.TransactionJpaRepository
import org.my.firstcircletest.data.repositories.postgres.UserJpaRepository
import org.my.firstcircletest.data.repositories.postgres.WalletJpaRepository
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.request.DepositRequestDto
import org.my.firstcircletest.delivery.http.dto.request.WithdrawRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.delivery.http.dto.response.DepositResponseDto
import org.my.firstcircletest.delivery.http.dto.response.WalletInfoResponseDto
import org.my.firstcircletest.delivery.http.dto.response.WithdrawResponseDto
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class DepositWithdrawalE2eTest {

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
    private var initialBalance: Int = 1000

    @BeforeEach
    fun setUp() {
        // Clean up database before each test
        transactionJpaRepository.deleteAll()
        walletJpaRepository.deleteAll()
        userJpaRepository.deleteAll()

        // Create a test user with initial balance
        val createUserRequest = CreateUserRequestDto(
            name = "Test User",
            initBalance = initialBalance
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
    fun `should complete full deposit and withdrawal flow with transaction records`() {
        // Step 1: Get initial wallet info
        mockMvc.perform(
            get("/api/users/$testUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.wallet_id").value(testWalletId))
            .andExpect(jsonPath("$.balance").value(initialBalance))

        // Step 2: Perform multiple deposits
        val firstDeposit = 500
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(DepositRequestDto(firstDeposit)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(initialBalance + firstDeposit))

        val secondDeposit = 300
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(DepositRequestDto(secondDeposit)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(initialBalance + firstDeposit + secondDeposit))

        // Step 3: Perform withdrawal
        val withdrawAmount = 400
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(WithdrawRequestDto(withdrawAmount)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(initialBalance + firstDeposit + secondDeposit - withdrawAmount))

        // Step 4: Verify final balance in database
        val wallet = walletJpaRepository.findById(testWalletId)
        assertTrue(wallet.isPresent)
        assertEquals(initialBalance + firstDeposit + secondDeposit - withdrawAmount, wallet.get().balance)

        // Step 5: Verify all transaction records are created
        val transactions = transactionJpaRepository.findByUserIdOrDestinationUserId(testUserId)
        assertEquals(3, transactions.size)
        assertEquals(2, transactions.count { it.type == TransactionType.DEPOSIT.name })
        assertEquals(1, transactions.count { it.type == TransactionType.WITHDRAWAL.name })
        assertTrue(transactions.all { it.status == "COMPLETED" })

        // Step 6: Verify wallet info reflects final state
        mockMvc.perform(
            get("/api/users/$testUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(initialBalance + firstDeposit + secondDeposit - withdrawAmount))
    }

    @Test
    fun `should handle withdrawal edge cases - insufficient balance and exact balance`() {
        // Step 1: Try to withdraw more than balance (should fail)
        val excessAmount = initialBalance + 500
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(WithdrawRequestDto(excessAmount)))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"))

        // Verify balance unchanged
        val walletAfterFail = walletJpaRepository.findById(testWalletId)
        assertEquals(initialBalance, walletAfterFail.get().balance)

        // Step 2: Withdraw exact balance (should succeed)
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(WithdrawRequestDto(initialBalance)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(0))

        // Verify wallet balance is zero
        val walletAfterSuccess = walletJpaRepository.findById(testWalletId)
        assertEquals(0, walletAfterSuccess.get().balance)

        // Verify only successful transaction was created
        val transactions = transactionJpaRepository.findByUserIdOrDestinationUserId(testUserId)
        assertEquals(1, transactions.size)
    }

    @Test
    fun `should fail with validation errors for invalid amounts and non-existent users`() {
        // Test 1: Invalid deposit amounts
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("amount" to 0)))
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/users/$testUserId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("amount" to -100)))
        )
            .andExpect(status().isBadRequest)

        // Test 2: Invalid withdrawal amounts
        mockMvc.perform(
            post("/api/users/$testUserId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("amount" to 0)))
        )
            .andExpect(status().isBadRequest)

        // Test 3: Non-existent user operations
        val nonExistentUserId = "user-00000000-0000-0000-0000-000000000000"

        mockMvc.perform(
            get("/api/users/$nonExistentUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))

        mockMvc.perform(
            post("/api/users/$nonExistentUserId/wallet/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(DepositRequestDto(500)))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))

        mockMvc.perform(
            post("/api/users/$nonExistentUserId/wallet/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(WithdrawRequestDto(500)))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))

        // Verify original user's balance is unchanged after all failed operations
        val wallet = walletJpaRepository.findById(testWalletId)
        assertEquals(initialBalance, wallet.get().balance)
    }
}
