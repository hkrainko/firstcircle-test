package org.my.firstcircletest.data.repositories.postgres

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitSingleOrNull
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class PgUserRepositoryIntegrationTest {

    @Autowired
    private lateinit var pgUserRepository: PgUserRepository

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Test
    fun `createUser should persist user successfully`() = runTest {
        // Given
        val request = CreateUserRequest(name = "John Doe", initBalance = 1000)

        // When
        val eitherResult = pgUserRepository.createUser(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertNotNull(result.id)
        assertTrue(result.id.toString().startsWith("user-"))
        assertEquals("John Doe", result.name)

        // Verify it was persisted
        val found = databaseClient.sql("SELECT * FROM users WHERE id = $1")
            .bind(0, result.id)
            .fetch()
            .awaitSingleOrNull()
        assertNotNull(found)
        assertEquals("John Doe", found?.get("name"))
    }

    @Test
    fun `createUser should handle special characters in name`() = runTest {
        // Given
        val request = CreateUserRequest(name = "O'Brien & Smith-Jones", initBalance = 2000)

        // When
        val eitherResult = pgUserRepository.createUser(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals("O'Brien & Smith-Jones", result.name)

        // Verify it was persisted correctly
        val found = databaseClient.sql("SELECT * FROM users WHERE id = $1")
            .bind(0, result.id)
            .fetch()
            .awaitSingleOrNull()
        assertEquals("O'Brien & Smith-Jones", found?.get("name"))
    }

    @Test
    fun `createUser should handle long names`() = runTest {
        // Given
        val longName = "A".repeat(100)
        val request = CreateUserRequest(name = longName, initBalance = 100)

        // When
        val eitherResult = pgUserRepository.createUser(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals(longName, result.name)
    }

    @Test
    fun `createUser should handle empty name`() = runTest {
        // Given
        val request = CreateUserRequest(name = "", initBalance = 0)

        // When
        val eitherResult = pgUserRepository.createUser(request)

        // Then
        assertTrue(eitherResult.isRight())
        val result = eitherResult.getOrElse { fail("Expected Right but got Left") }
        assertNotNull(result)
        assertEquals("", result.name)
    }

    @Test
    fun `createUser should create multiple users with same name`() = runTest {
        // Given
        val request1 = CreateUserRequest(name = "John Smith", initBalance = 300)
        val request2 = CreateUserRequest(name = "John Smith", initBalance = 400)

        // When
        val eitherResult1 = pgUserRepository.createUser(request1)
        val eitherResult2 = pgUserRepository.createUser(request2)

        // Then
        assertTrue(eitherResult1.isRight())
        assertTrue(eitherResult2.isRight())
        val result1 = eitherResult1.getOrElse { fail("Expected Right but got Left") }
        val result2 = eitherResult2.getOrElse { fail("Expected Right but got Left") }
        assertNotEquals(result1.id, result2.id)
        assertEquals(result1.name, result2.name)
    }
}
