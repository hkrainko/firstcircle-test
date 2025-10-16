package org.my.firstcircletest.data.repositories.postgres

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@Import(PgUserRepository::class)
class PgUserRepositoryIntegrationTest {

    @Autowired
    private lateinit var pgUserRepository: PgUserRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `createUser should persist user successfully`() {
        // Given
        val request = CreateUserRequest(name = "John Doe")

        // When
        val result = pgUserRepository.createUser(request)

        // Then
        assertNotNull(result)
        assertNotNull(result.id)
        assertTrue(result.id.toString().startsWith("user-"))
        assertEquals("John Doe", result.name)

        // Verify it was persisted
        val found = entityManager.find(
            org.my.firstcircletest.data.repositories.postgres.dto.UserDTO::class.java,
            result.id.toString()
        )
        assertNotNull(found)
        assertEquals("John Doe", found.name)
    }

    @Test
    fun `createUser should generate unique IDs for different users`() {
        // Given
        val request1 = CreateUserRequest(name = "User One")
        val request2 = CreateUserRequest(name = "User Two")

        // When
        val result1 = pgUserRepository.createUser(request1)
        val result2 = pgUserRepository.createUser(request2)

        // Then
        assertNotNull(result1.id)
        assertNotNull(result2.id)
        assertNotEquals(result1.id, result2.id)
        assertTrue(result1.id.toString().startsWith("user-"))
        assertTrue(result2.id.toString().startsWith("user-"))
        assertEquals("User One", result1.name)
        assertEquals("User Two", result2.name)
    }

    @Test
    fun `createUser should handle special characters in name`() {
        // Given
        val request = CreateUserRequest(name = "O'Brien & Smith-Jones")

        // When
        val result = pgUserRepository.createUser(request)

        // Then
        assertNotNull(result)
        assertEquals("O'Brien & Smith-Jones", result.name)

        // Verify it was persisted correctly
        val found = entityManager.find(
            org.my.firstcircletest.data.repositories.postgres.dto.UserDTO::class.java,
            result.id.toString()
        )
        assertEquals("O'Brien & Smith-Jones", found.name)
    }

    @Test
    fun `createUser should handle long names`() {
        // Given
        val longName = "A".repeat(255)
        val request = CreateUserRequest(name = longName)

        // When
        val result = pgUserRepository.createUser(request)

        // Then
        assertNotNull(result)
        assertEquals(longName, result.name)
    }

    @Test
    fun `createUser should handle empty name`() {
        // Given
        val request = CreateUserRequest(name = "")

        // When
        val result = pgUserRepository.createUser(request)

        // Then
        assertNotNull(result)
        assertEquals("", result.name)
    }

    @Test
    fun `createUser should create multiple users with same name`() {
        // Given
        val request1 = CreateUserRequest(name = "John Smith")
        val request2 = CreateUserRequest(name = "John Smith")

        // When
        val result1 = pgUserRepository.createUser(request1)
        val result2 = pgUserRepository.createUser(request2)

        // Then
        assertNotEquals(result1.id, result2.id)
        assertEquals(result1.name, result2.name)
    }
}
