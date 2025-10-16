package org.my.firstcircletest.data.repositories.postgres

import org.my.firstcircletest.data.repositories.postgres.dto.UserDTO
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.my.firstcircletest.domain.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class PgUserRepository(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {
    private val logger = LoggerFactory.getLogger(PgUserRepository::class.java)

    override fun createUser(request: CreateUserRequest): User {
        return try {
            val userId = "user-${UUID.randomUUID()}"

            val userDTO = UserDTO(
                id = userId,
                name = request.name
            )

            val saved = userJpaRepository.save(userDTO)
            saved.toDomain()
        } catch (e: Exception) {
            logger.error("PgUserRepo.createUser: error executing query", e)
            throw DomainError.DatabaseException("Error creating user")
        }
    }
}

interface UserJpaRepository : JpaRepository<UserDTO, String>
