package org.my.firstcircletest

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.data.repositories.postgres.UserJpaRepository
import org.my.firstcircletest.data.repositories.postgres.WalletJpaRepository
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class CreateUserE2eTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var walletJpaRepository: WalletJpaRepository

    @BeforeEach
    fun setUp() {
        // Clean up database before each test
        walletJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
    }

    @Test
    fun `should successfully create user with initial balance and wallet`() {
        // Given
        val requestDto = CreateUserRequestDto(
            name = "John Doe",
            initBalance = 1000
        )

        val requestBody = objectMapper.writeValueAsString(requestDto)

        // When
        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )

        // Then
        result
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user_id").exists())
            .andExpect(jsonPath("$.name").value("John Doe"))

        val responseJson = result.andReturn().response.contentAsString
        val response = objectMapper.readValue(responseJson, CreateUserResponseDto::class.java)

        // Verify user is persisted in database
        val userEntity = userJpaRepository.findById(response.userId)
        assertTrue(userEntity.isPresent)
        assertEquals("John Doe", userEntity.get().name)

        // Verify wallet is created with correct balance
        val walletEntity = walletJpaRepository.findByUserId(response.userId)
        assertTrue(walletEntity.isPresent)
        assertEquals(response.userId, walletEntity.get().userId)
        assertEquals(1000, walletEntity.get().balance)
    }

    @Test
    fun `should successfully create user with default zero balance when not specified`() {
        // Given
        val requestDto = CreateUserRequestDto(
            name = "Jane Smith"
        )

        val requestBody = objectMapper.writeValueAsString(requestDto)

        // When
        val result = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )

        // Then
        result
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user_id").exists())
            .andExpect(jsonPath("$.name").value("Jane Smith"))

        val responseJson = result.andReturn().response.contentAsString
        val response = objectMapper.readValue(responseJson, CreateUserResponseDto::class.java)

        // Verify wallet is created with zero balance
        val walletEntity = walletJpaRepository.findByUserId(response.userId)
        assertTrue(walletEntity.isPresent)
        assertEquals(0, walletEntity.get().balance)
    }

    @Test
    fun `should fail when name is invalid`() {
        // Given
        val requestDto = mapOf(
            "name" to "",
            "init_balance" to 1000
        )

        val requestBody = objectMapper.writeValueAsString(requestDto)

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)

        // Verify no user or wallet is persisted
        assertEquals(0, userJpaRepository.count())
        assertEquals(0, walletJpaRepository.count())
    }

    @Test
    fun `should fail when initial balance is negative`() {
        // Given
        val requestDto = mapOf(
            "name" to "John Doe",
            "init_balance" to -100
        )

        val requestBody = objectMapper.writeValueAsString(requestDto)

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)

        // Verify no user or wallet is persisted
        assertEquals(0, userJpaRepository.count())
        assertEquals(0, walletJpaRepository.count())
    }

    @Test
    fun `should fail when initial balance exceeds maximum limit`() {
        // Given
        val requestDto = mapOf(
            "name" to "John Doe",
            "init_balance" to 100_000_001
        )

        val requestBody = objectMapper.writeValueAsString(requestDto)

        // When & Then
        mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)

        // Verify no user or wallet is persisted
        assertEquals(0, userJpaRepository.count())
        assertEquals(0, walletJpaRepository.count())
    }

    @Test
    fun `should create multiple users independently`() {
        // Given
        val user1Request = CreateUserRequestDto(name = "User One", initBalance = 500)
        val user2Request = CreateUserRequestDto(name = "User Two", initBalance = 1500)

        // When
        val result1 = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user1Request))
        ).andExpect(status().isCreated).andReturn()

        val result2 = mockMvc.perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user2Request))
        ).andExpect(status().isCreated).andReturn()

        // Then
        val response1 = objectMapper.readValue(
            result1.response.contentAsString,
            CreateUserResponseDto::class.java
        )
        val response2 = objectMapper.readValue(
            result2.response.contentAsString,
            CreateUserResponseDto::class.java
        )

        // Verify both users exist with different IDs
        assertNotEquals(response1.userId, response2.userId)
        assertEquals(2, userJpaRepository.count())
        assertEquals(2, walletJpaRepository.count())

        // Verify each user has correct wallet
        val wallet1 = walletJpaRepository.findByUserId(response1.userId)
        val wallet2 = walletJpaRepository.findByUserId(response2.userId)

        assertTrue(wallet1.isPresent)
        assertTrue(wallet2.isPresent)
        assertEquals(500, wallet1.get().balance)
        assertEquals(1500, wallet2.get().balance)
    }
}