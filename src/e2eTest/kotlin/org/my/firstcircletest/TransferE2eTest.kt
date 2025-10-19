package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.TransactionReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.UserReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.WalletReactiveRepository
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.request.TransferRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.delivery.http.dto.response.TransferResponseDto
import org.my.firstcircletest.domain.entities.TransactionType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class TransferE2eTest {

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

    private lateinit var senderUserId: String
    private lateinit var senderWalletId: String
    private var senderInitialBalance: Long = 2000L

    private lateinit var receiverUserId: String
    private lateinit var receiverWalletId: String
    private var receiverInitialBalance: Long = 1000L

    @BeforeEach
    fun setUp() = runBlocking {
        // Clean up database before each test
        transactionReactiveRepository.deleteAll()
        walletReactiveRepository.deleteAll()
        userReactiveRepository.deleteAll()

        // Create sender user
        val senderRequest = CreateUserRequestDto(
            name = "Sender User",
            initBalance = senderInitialBalance
        )

        val senderResponse = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(senderRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(CreateUserResponseDto::class.java)
            .returnResult()
            .responseBody!!

        senderUserId = senderResponse.userId

        val senderWallet = walletReactiveRepository.findByUserId(senderUserId)
        assertNotNull(senderWallet)
        senderWalletId = senderWallet!!.id

        // Create receiver user
        val receiverRequest = CreateUserRequestDto(
            name = "Receiver User",
            initBalance = receiverInitialBalance
        )

        val receiverResponse = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(receiverRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(CreateUserResponseDto::class.java)
            .returnResult()
            .responseBody!!

        receiverUserId = receiverResponse.userId

        val receiverWallet = walletReactiveRepository.findByUserId(receiverUserId)
        assertNotNull(receiverWallet)
        receiverWalletId = receiverWallet!!.id
    }

    @Test
    fun `should complete successful transfer and verify balances for both users`() = runBlocking {
        // Given
        val transferAmount = 500L
        val transferRequest = TransferRequestDto(
            toUserId = receiverUserId,
            amount = transferAmount
        )

        // Step 1: Check initial balances
        webTestClient.get()
            .uri("/api/users/$senderUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(senderInitialBalance)

        webTestClient.get()
            .uri("/api/users/$receiverUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(receiverInitialBalance)

        // Step 2: Perform transfer
        val response = webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(transferRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody(TransferResponseDto::class.java)
            .returnResult()
            .responseBody!!

        // Step 3: Verify transfer response
        assertEquals(senderUserId, response.fromUserId)
        assertEquals(receiverUserId, response.toUserId)
        assertEquals(transferAmount, response.amount)

        // Step 4: Verify sender's balance decreased
        webTestClient.get()
            .uri("/api/users/$senderUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(senderInitialBalance - transferAmount)

        val senderWallet = walletReactiveRepository.findById(senderWalletId)
        assertEquals(senderInitialBalance - transferAmount, senderWallet!!.balance)

        // Step 5: Verify receiver's balance increased
        webTestClient.get()
            .uri("/api/users/$receiverUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(receiverInitialBalance + transferAmount)

        val receiverWallet = walletReactiveRepository.findById(receiverWalletId)
        assertEquals(receiverInitialBalance + transferAmount, receiverWallet!!.balance)

        // Step 6: Verify transaction records for both users
        val senderTransactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(senderUserId).toList()
        val receiverTransactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(receiverUserId).toList()

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
    fun `should handle multiple transfers and verify transaction history`(): Unit = runBlocking {
        // Step 1: First transfer - sender to receiver
        val firstTransfer = 300L
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TransferRequestDto(receiverUserId, firstTransfer))
            .exchange()
            .expectStatus().isOk

        // Step 2: Second transfer - sender to receiver
        val secondTransfer = 200L
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TransferRequestDto(receiverUserId, secondTransfer))
            .exchange()
            .expectStatus().isOk

        // Step 3: Reverse transfer - receiver to sender
        val reverseTransfer = 100L
        webTestClient.post()
            .uri("/api/users/$receiverUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TransferRequestDto(senderUserId, reverseTransfer))
            .exchange()
            .expectStatus().isOk

        // Step 4: Verify final balances
        val expectedSenderBalance = senderInitialBalance - firstTransfer - secondTransfer + reverseTransfer
        val expectedReceiverBalance = receiverInitialBalance + firstTransfer + secondTransfer - reverseTransfer

        webTestClient.get()
            .uri("/api/users/$senderUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(expectedSenderBalance)

        webTestClient.get()
            .uri("/api/users/$receiverUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(expectedReceiverBalance)

        // Step 5: Verify transaction counts
        val senderTransactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(senderUserId).toList()
        val receiverTransactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(receiverUserId).toList()

        // Sender has 2 outgoing + 1 incoming = 3 total
        assertEquals(3, senderTransactions.size)
        // Receiver has 2 incoming + 1 outgoing = 3 total
        assertEquals(3, receiverTransactions.size)

        // Step 6: Verify transaction history via API
        webTestClient.get()
            .uri("/api/users/$senderUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.transactions.length()").isEqualTo(3)

        webTestClient.get()
            .uri("/api/users/$receiverUserId/transactions")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.transactions.length()").isEqualTo(3)
    }

    @Test
    fun `should fail transfer with insufficient balance and other validation errors`() = runBlocking {
        // Test 1: Insufficient balance
        val excessAmount = senderInitialBalance + 500
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TransferRequestDto(receiverUserId, excessAmount))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INSUFFICIENT_BALANCE")

        // Test 2: Same user transfer
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TransferRequestDto(senderUserId, 100))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("SAME_USER_TRANSFER")

        // Test 3: Non-positive amount (zero)
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("to_user_id" to receiverUserId, "amount" to 0))
            .exchange()
            .expectStatus().isBadRequest

        // Test 4: Negative amount
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("to_user_id" to receiverUserId, "amount" to -100))
            .exchange()
            .expectStatus().isBadRequest

        // Test 5: Non-existent receiver
        val nonExistentUserId = "user-00000000-0000-0000-0000-000000000000"
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(TransferRequestDto(nonExistentUserId, 100))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")

        // Verify balances unchanged after all failed transfers (using API to get fresh data)
        webTestClient.get()
            .uri("/api/users/$senderUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(senderInitialBalance)

        webTestClient.get()
            .uri("/api/users/$receiverUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(receiverInitialBalance)

        // Double check with database
        val senderWallet = walletReactiveRepository.findById(senderWalletId)
        val receiverWallet = walletReactiveRepository.findById(receiverWalletId)
        assertEquals(senderInitialBalance, senderWallet!!.balance)
        assertEquals(receiverInitialBalance, receiverWallet!!.balance)

        // Verify no transactions created
        val senderTransactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(senderUserId).toList()
        val receiverTransactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(receiverUserId).toList()
        assertEquals(0, senderTransactions.size)
        assertEquals(0, receiverTransactions.size)
    }

    @Test
    fun `should allow transfer of exact balance amount`() = runBlocking {
        // Given - Transfer exact balance
        val transferRequest = TransferRequestDto(
            toUserId = receiverUserId,
            amount = senderInitialBalance
        )

        // When
        webTestClient.post()
            .uri("/api/users/$senderUserId/wallet/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(transferRequest)
            .exchange()
            .expectStatus().isOk

        // Then - Sender has zero balance
        webTestClient.get()
            .uri("/api/users/$senderUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(0)

        // Receiver has original + transferred amount
        webTestClient.get()
            .uri("/api/users/$receiverUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(receiverInitialBalance + senderInitialBalance)

        // Verify in database
        val senderWallet = walletReactiveRepository.findById(senderWalletId)
        val receiverWallet = walletReactiveRepository.findById(receiverWalletId)
        assertEquals(0, senderWallet!!.balance)
        assertEquals(receiverInitialBalance + senderInitialBalance, receiverWallet!!.balance)
    }
}
