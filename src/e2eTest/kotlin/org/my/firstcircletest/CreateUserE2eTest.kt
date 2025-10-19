package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.UserReactiveRepository
import org.my.firstcircletest.data.repositories.postgres.WalletReactiveRepository
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class CreateUserE2eTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userReactiveRepository: UserReactiveRepository

    @Autowired
    private lateinit var walletReactiveRepository: WalletReactiveRepository

    @BeforeEach
    fun setUp() = runBlocking {
        // Clean up database before each test
        walletReactiveRepository.deleteAll()
        userReactiveRepository.deleteAll()
    }

    @Test
    fun `should successfully create user with initial balance and wallet`() = runBlocking {
        // Given
        val requestDto = CreateUserRequestDto(
            name = "John Doe",
            initBalance = 1000
        )

        // When & Then
        val response = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isCreated
            .expectBody<CreateUserResponseDto>()
            .returnResult()
            .responseBody!!

        assertEquals("John Doe", response.name)

        // Verify user is persisted in database
        val userEntity = userReactiveRepository.findById(response.userId)
        assertNotNull(userEntity)
        assertEquals("John Doe", userEntity!!.name)

        // Verify wallet is created with correct balance
        val walletEntity = walletReactiveRepository.findByUserId(response.userId)
        assertNotNull(walletEntity)
        assertEquals(response.userId, walletEntity!!.userId)
        assertEquals(1000, walletEntity.balance)
    }

    @Test
    fun `should successfully create user with default zero balance when not specified`() = runBlocking {
        // Given
        val requestDto = CreateUserRequestDto(
            name = "Jane Smith"
        )

        // When & Then
        val response = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isCreated
            .expectBody<CreateUserResponseDto>()
            .returnResult()
            .responseBody!!

        assertEquals("Jane Smith", response.name)

        // Verify wallet is created with zero balance
        val walletEntity = walletReactiveRepository.findByUserId(response.userId)
        assertNotNull(walletEntity)
        assertEquals(0, walletEntity!!.balance)
    }

    @Test
    fun `should fail when name is invalid`() = runBlocking {
        // Given
        val requestDto = mapOf(
            "name" to "",
            "init_balance" to 1000
        )

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest

        // Verify no user or wallet is persisted
        assertEquals(0, userReactiveRepository.count())
        assertEquals(0, walletReactiveRepository.count())
    }

    @Test
    fun `should fail when initial balance is negative`() = runBlocking {
        // Given
        val requestDto = mapOf(
            "name" to "John Doe",
            "init_balance" to -100
        )

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest

        // Verify no user or wallet is persisted
        assertEquals(0, userReactiveRepository.count())
        assertEquals(0, walletReactiveRepository.count())
    }

    @Test
    fun `should fail when initial balance exceeds maximum limit`() = runBlocking {
        // Given
        val requestDto = mapOf(
            "name" to "John Doe",
            "init_balance" to 100_000_001
        )

        // When & Then
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().isBadRequest

        // Verify no user or wallet is persisted
        assertEquals(0, userReactiveRepository.count())
        assertEquals(0, walletReactiveRepository.count())
    }

    @Test
    fun `should create multiple users independently`() = runBlocking {
        // Given
        val user1Request = CreateUserRequestDto(name = "User One", initBalance = 500)
        val user2Request = CreateUserRequestDto(name = "User Two", initBalance = 1500)

        // When
        val response1 = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(user1Request)
            .exchange()
            .expectStatus().isCreated
            .expectBody<CreateUserResponseDto>()
            .returnResult()
            .responseBody!!

        val response2 = webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(user2Request)
            .exchange()
            .expectStatus().isCreated
            .expectBody<CreateUserResponseDto>()
            .returnResult()
            .responseBody!!

        // Then
        // Verify both users exist with different IDs
        assertNotEquals(response1.userId, response2.userId)
        assertEquals(2, userReactiveRepository.count())
        assertEquals(2, walletReactiveRepository.count())

        // Verify each user has correct wallet
        val wallet1 = walletReactiveRepository.findByUserId(response1.userId)
        val wallet2 = walletReactiveRepository.findByUserId(response2.userId)

        assertNotNull(wallet1)
        assertNotNull(wallet2)
        assertEquals(500, wallet1!!.balance)
        assertEquals(1500, wallet2!!.balance)
    }
}