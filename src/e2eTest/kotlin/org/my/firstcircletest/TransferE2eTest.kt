package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.TransactionJpaRepository
import org.my.firstcircletest.data.repositories.postgres.UserJpaRepository
import org.my.firstcircletest.data.repositories.postgres.WalletJpaRepository
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.request.TransferRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.delivery.http.dto.response.TransferResponseDto
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
class TransferE2eTest {

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

    private lateinit var senderUserId: String
    private lateinit var senderWalletId: String
    private var senderInitialBalance: Int = 2000

    private lateinit var receiverUserId: String
    private lateinit var receiverWalletId: String
    private var receiverInitialBalance: Int = 1000

    @BeforeEach
    fun setUp() {
        // Clean up database before each test
        transactionJpaRepository.deleteAll()
        walletJpaRepository.deleteAll()
        userJpaRepository.deleteAll()

        // Create sender user
        val senderRequest = CreateUserRequestDto(
            name = "Sender User",
            initBalance = senderInitialBalance
        )

        val senderResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(senderRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val senderResponse = objectMapper.readValue(
            senderResult.response.contentAsString,
            CreateUserResponseDto::class.java
        )
        senderUserId = senderResponse.userId

        val senderWallet = walletJpaRepository.findByUserId(senderUserId)
        assertTrue(senderWallet.isPresent)
        senderWalletId = senderWallet.get().id

        // Create receiver user
        val receiverRequest = CreateUserRequestDto(
            name = "Receiver User",
            initBalance = receiverInitialBalance
        )

        val receiverResult = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(receiverRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val receiverResponse = objectMapper.readValue(
            receiverResult.response.contentAsString,
            CreateUserResponseDto::class.java
        )
        receiverUserId = receiverResponse.userId

        val receiverWallet = walletJpaRepository.findByUserId(receiverUserId)
        assertTrue(receiverWallet.isPresent)
        receiverWalletId = receiverWallet.get().id
    }

    @Test
    fun `should complete successful transfer and verify balances for both users`() {
        // Given
        val transferAmount = 500
        val transferRequest = TransferRequestDto(
            toUserId = receiverUserId,
            amount = transferAmount
        )

        // Step 1: Check initial balances
        mockMvc.perform(
            get("/api/users/$senderUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(senderInitialBalance))

        mockMvc.perform(
            get("/api/users/$receiverUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(receiverInitialBalance))

        // Step 2: Perform transfer
        val transferResult = mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest))
        )

        // Step 3: Verify transfer response
        transferResult
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from_user_id").value(senderUserId))
            .andExpect(jsonPath("$.to_user_id").value(receiverUserId))
            .andExpect(jsonPath("$.amount").value(transferAmount))

        val responseJson = transferResult.andReturn().response.contentAsString
        val response = objectMapper.readValue(responseJson, TransferResponseDto::class.java)
        assertEquals(senderUserId, response.fromUserId)
        assertEquals(receiverUserId, response.toUserId)
        assertEquals(transferAmount, response.amount)

        // Step 4: Verify sender's balance decreased
        mockMvc.perform(
            get("/api/users/$senderUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(senderInitialBalance - transferAmount))

        val senderWallet = walletJpaRepository.findById(senderWalletId)
        assertEquals(senderInitialBalance - transferAmount, senderWallet.get().balance)

        // Step 5: Verify receiver's balance increased
        mockMvc.perform(
            get("/api/users/$receiverUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(receiverInitialBalance + transferAmount))

        val receiverWallet = walletJpaRepository.findById(receiverWalletId)
        assertEquals(receiverInitialBalance + transferAmount, receiverWallet.get().balance)

        // Step 6: Verify transaction records for both users
        val senderTransactions = transactionJpaRepository.findByUserIdOrDestinationUserId(senderUserId)
        val receiverTransactions = transactionJpaRepository.findByUserIdOrDestinationUserId(receiverUserId)

        // Sender should have 1 transfer transaction (outgoing)
        assertEquals(1, senderTransactions.size)
        val senderTx = senderTransactions[0]
        assertEquals(TransactionType.TRANSFER.name, senderTx.type)
        assertEquals(senderWalletId, senderTx.walletId)
        assertEquals(receiverWalletId, senderTx.destinationWalletId)
        assertEquals(transferAmount, senderTx.amount)

        // Receiver should have 1 transfer transaction (incoming)
        assertEquals(1, receiverTransactions.size)
        val receiverTx = receiverTransactions[0]
        assertEquals(TransactionType.TRANSFER.name, receiverTx.type)
        assertEquals(receiverUserId, receiverTx.destinationUserId)
    }

    @Test
    fun `should handle multiple transfers and verify transaction history`() {
        // Step 1: First transfer - sender to receiver
        val firstTransfer = 300
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferRequestDto(receiverUserId, firstTransfer)))
        )
            .andExpect(status().isOk)

        // Step 2: Second transfer - sender to receiver
        val secondTransfer = 200
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferRequestDto(receiverUserId, secondTransfer)))
        )
            .andExpect(status().isOk)

        // Step 3: Reverse transfer - receiver to sender
        val reverseTransfer = 100
        mockMvc.perform(
            post("/api/users/$receiverUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferRequestDto(senderUserId, reverseTransfer)))
        )
            .andExpect(status().isOk)

        // Step 4: Verify final balances
        val expectedSenderBalance = senderInitialBalance - firstTransfer - secondTransfer + reverseTransfer
        val expectedReceiverBalance = receiverInitialBalance + firstTransfer + secondTransfer - reverseTransfer

        mockMvc.perform(
            get("/api/users/$senderUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(expectedSenderBalance))

        mockMvc.perform(
            get("/api/users/$receiverUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(expectedReceiverBalance))

        // Step 5: Verify transaction counts
        val senderTransactions = transactionJpaRepository.findByUserIdOrDestinationUserId(senderUserId)
        val receiverTransactions = transactionJpaRepository.findByUserIdOrDestinationUserId(receiverUserId)

        // Sender has 2 outgoing + 1 incoming = 3 total
        assertEquals(3, senderTransactions.size)
        // Receiver has 2 incoming + 1 outgoing = 3 total
        assertEquals(3, receiverTransactions.size)

        // Step 6: Verify transaction history via API
        mockMvc.perform(
            get("/api/users/$senderUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactions.length()").value(3))

        mockMvc.perform(
            get("/api/users/$receiverUserId/transactions")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactions.length()").value(3))
    }

    @Test
    fun `should fail transfer with insufficient balance and other validation errors`() {
        // Test 1: Insufficient balance
        val excessAmount = senderInitialBalance + 500
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferRequestDto(receiverUserId, excessAmount)))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"))

        // Test 2: Same user transfer
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferRequestDto(senderUserId, 100)))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("SAME_USER_TRANSFER"))

        // Test 3: Non-positive amount (zero)
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("to_user_id" to receiverUserId, "amount" to 0)))
        )
            .andExpect(status().isBadRequest)

        // Test 4: Negative amount
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("to_user_id" to receiverUserId, "amount" to -100)))
        )
            .andExpect(status().isBadRequest)

        // Test 5: Non-existent receiver
        val nonExistentUserId = "user-00000000-0000-0000-0000-000000000000"
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferRequestDto(nonExistentUserId, 100)))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))

        // Verify balances unchanged after all failed transfers (using API to get fresh data)
        mockMvc.perform(
            get("/api/users/$senderUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(senderInitialBalance))

        mockMvc.perform(
            get("/api/users/$receiverUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(receiverInitialBalance))

        // Double check with database
        walletJpaRepository.flush()
        val senderWallet = walletJpaRepository.findById(senderWalletId)
        val receiverWallet = walletJpaRepository.findById(receiverWalletId)
        assertEquals(senderInitialBalance, senderWallet.get().balance)
        assertEquals(receiverInitialBalance, receiverWallet.get().balance)

        // Verify no transactions created
        val senderTransactions = transactionJpaRepository.findByUserIdOrDestinationUserId(senderUserId)
        val receiverTransactions = transactionJpaRepository.findByUserIdOrDestinationUserId(receiverUserId)
        assertEquals(0, senderTransactions.size)
        assertEquals(0, receiverTransactions.size)
    }

    @Test
    fun `should allow transfer of exact balance amount`() {
        // Given - Transfer exact balance
        val transferRequest = TransferRequestDto(
            toUserId = receiverUserId,
            amount = senderInitialBalance
        )

        // When
        mockMvc.perform(
            post("/api/users/$senderUserId/wallet/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest))
        )
            .andExpect(status().isOk)

        // Then - Sender has zero balance
        mockMvc.perform(
            get("/api/users/$senderUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(0))

        // Receiver has original + transferred amount
        mockMvc.perform(
            get("/api/users/$receiverUserId/wallet")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(receiverInitialBalance + senderInitialBalance))

        // Verify in database
        val senderWallet = walletJpaRepository.findById(senderWalletId)
        val receiverWallet = walletJpaRepository.findById(receiverWalletId)
        assertEquals(0, senderWallet.get().balance)
        assertEquals(receiverInitialBalance + senderInitialBalance, receiverWallet.get().balance)
    }
}
