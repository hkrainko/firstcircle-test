package org.my.firstcircletest.data.repositories.postgres

import arrow.core.Either
import kotlinx.coroutines.reactive.awaitSingle
import org.my.firstcircletest.data.repositories.postgres.entities.UserEntity
import org.my.firstcircletest.domain.entities.CreateUserRequest
import org.my.firstcircletest.domain.entities.User
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class PgUserRepository(
    private val userReactiveRepository: UserReactiveRepository
) : UserRepository {
    private val logger = LoggerFactory.getLogger(PgUserRepository::class.java)

    override suspend fun createUser(request: CreateUserRequest): Either<RepositoryError, User> {
        return Either.catch {
            val userEntity = UserEntity.newUser(request.name)
            val saved = userReactiveRepository.save(userEntity)
            saved.toDomain()
        }.mapLeft { e ->
            logger.error("PgUserRepo.createUser: error executing query", e)
            RepositoryError.CreationFailed("Error creating user")
        }
    }

    override suspend fun getUserById(userId: String): Either<RepositoryError, User> {
        return Either.catch {
            val userEntity = userReactiveRepository.findById(userId)
            if (userEntity == null) {
                logger.warn("PgUserRepo.getUserById: User not found with id: $userId")
                return Either.Left(RepositoryError.NotFound("User not found with id: $userId"))
            }
            userEntity.toDomain()
        }.mapLeft { e ->
            logger.error("PgUserRepo.getUserById: error executing query", e)
            RepositoryError.RetrievalFailed("Error retrieving user")
        }
    }
}

interface UserReactiveRepository : CoroutineCrudRepository<UserEntity, String>
