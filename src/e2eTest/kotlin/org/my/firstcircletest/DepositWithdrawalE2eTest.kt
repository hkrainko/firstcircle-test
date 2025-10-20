package org.my.firstcircletest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.TransactionReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.UserReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.WalletReactiveRepository
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.request.DepositRequestDto
import org.my.firstcircletest.delivery.http.dto.request.WithdrawRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.domain.entities.TransactionType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class DepositWithdrawalE2eTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userReactiveRepository: UserReactiveRepository

    @Autowired
    private lateinit var walletReactiveRepository: WalletReactiveRepository

    @Autowired
    private lateinit var transactionReactiveRepository: TransactionReactiveRepository

    private lateinit var testUserId: String
    private lateinit var testWalletId: String
    private var initialBalance: Long = 1000L

    @BeforeEach
    fun setUp() = runBlocking {
        // Clean up database before each test
        transactionReactiveRepository.deleteAll()
        walletReactiveRepository.deleteAll()
        userReactiveRepository.deleteAll()

        // Create a test user with initial balance
        val createUserRequest = CreateUserRequestDto(
            name = "Test User",
            initBalance = initialBalance
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
    fun `should complete full deposit and withdrawal flow with transaction records`(): Unit = runBlocking {
        // Step 1: Get initial wallet info
        webTestClient.get()
            .uri("/api/users/$testUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.wallet_id").isEqualTo(testWalletId)
            .jsonPath("$.balance").isEqualTo(initialBalance)

        // Step 2: Perform multiple deposits
        val firstDeposit = 500L
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(DepositRequestDto(firstDeposit))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.amount").isEqualTo(initialBalance + firstDeposit)

        val secondDeposit = 300L
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(DepositRequestDto(secondDeposit))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.amount").isEqualTo(initialBalance + firstDeposit + secondDeposit)

        // Step 3: Perform withdrawal
        val withdrawAmount = 400L
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WithdrawRequestDto(withdrawAmount))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(initialBalance + firstDeposit + secondDeposit - withdrawAmount)

        // Step 4: Verify final balance in database
        val wallet = walletReactiveRepository.findById(testWalletId)
        assertNotNull(wallet)
        assertEquals(initialBalance + firstDeposit + secondDeposit - withdrawAmount, wallet!!.balance)

        // Step 5: Verify all transaction records are created
        val transactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(testUserId).toList()
        assertEquals(3, transactions.size)
        assertEquals(2, transactions.count { it.type == TransactionType.DEPOSIT.name })
        assertEquals(1, transactions.count { it.type == TransactionType.WITHDRAWAL.name })
        assertTrue(transactions.all { it.status == "COMPLETED" })

        // Step 6: Verify wallet info reflects final state
        webTestClient.get()
            .uri("/api/users/$testUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(initialBalance + firstDeposit + secondDeposit - withdrawAmount)
    }

    @Test
    fun `should handle withdrawal edge cases - insufficient balance and exact balance`() = runBlocking {
        // Step 1: Try to withdraw more than balance (should fail)
        val excessAmount = initialBalance + 500
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WithdrawRequestDto(excessAmount))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("INSUFFICIENT_BALANCE")

        // Verify balance unchanged
        val walletAfterFail = walletReactiveRepository.findById(testWalletId)
        assertEquals(initialBalance, walletAfterFail!!.balance)

        // Step 2: Withdraw exact balance (should succeed)
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WithdrawRequestDto(initialBalance))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.balance").isEqualTo(0)

        // Verify wallet balance is zero
        val walletAfterSuccess = walletReactiveRepository.findById(testWalletId)
        assertEquals(0, walletAfterSuccess!!.balance)

        // Verify only successful transaction was created
        val transactions = transactionReactiveRepository.findByUserIdOrDestinationUserId(testUserId).toList()
        assertEquals(1, transactions.size)
    }

    @Test
    fun `should fail with validation errors for invalid amounts and non-existent users`() = runBlocking {
        // Test 1: Invalid deposit amounts
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("amount" to 0))
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("amount" to -100))
            .exchange()
            .expectStatus().isBadRequest

        // Test 2: Invalid withdrawal amounts
        webTestClient.post()
            .uri("/api/users/$testUserId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("amount" to 0))
            .exchange()
            .expectStatus().isBadRequest

        // Test 3: Non-existent user operations
        val nonExistentUserId = "user-00000000-0000-0000-0000-000000000000"

        webTestClient.get()
            .uri("/api/users/$nonExistentUserId/wallet")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")

        webTestClient.post()
            .uri("/api/users/$nonExistentUserId/wallet/deposit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(DepositRequestDto(500))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")

        webTestClient.post()
            .uri("/api/users/$nonExistentUserId/wallet/withdraw")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(WithdrawRequestDto(500))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("WALLET_NOT_FOUND")

        // Verify original user's balance is unchanged after all failed operations
        val wallet = walletReactiveRepository.findById(testWalletId)
        assertEquals(initialBalance, wallet!!.balance)
    }
}
