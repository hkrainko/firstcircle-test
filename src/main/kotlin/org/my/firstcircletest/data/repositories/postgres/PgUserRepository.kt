package org.my.firstcircletest.data.repositories.postgres

import jakarta.persistence.EntityManager
import org.my.firstcircletest.data.repositories.postgres.dto.UserDTO
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class PgUserRepository(
    private val entityManager: EntityManager
): UserRepository {
    private val logger = LoggerFactory.getLogger(PgUserRepository::class.java)

    override fun createUser(request: CreateUserRequest): User {
        return try {
            val userId = "user-${UUID.randomUUID()}"

            val query = """
                INSERT INTO users (id, name) 
                VALUES (:id, :name)
            """.trimIndent()

            entityManager.createNativeQuery(query)
                .setParameter("id", userId)
                .setParameter("name", request.name)
                .executeUpdate()

            // Retrieve the created user
            val selectQuery = """
                SELECT id, name 
                FROM users 
                WHERE id = :id
            """.trimIndent()

            val result = entityManager.createNativeQuery(selectQuery, UserDTO::class.java)
                .setParameter("id", userId)
                .singleResult as UserDTO

            result.toDomain()
        } catch (e: Exception) {
            logger.error("PgUserRepo.createUser: error executing query", e)
            throw DomainError.DatabaseException("Error creating user")
        }
    }
}
