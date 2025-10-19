package org.my.firstcircletest.delivery.http.controllers

import jakarta.validation.Valid
import org.my.firstcircletest.delivery.http.dto.request.CreateUserRequestDto
import org.my.firstcircletest.delivery.http.dto.response.CreateUserResponseDto
import org.my.firstcircletest.delivery.http.dto.response.ErrorResponseDto
import org.my.firstcircletest.delivery.http.dto.response.GetUserTransactionsResponseDto
import org.my.firstcircletest.delivery.http.validation.ValidUserId
import org.my.firstcircletest.domain.usecases.CreateUserError
import org.my.firstcircletest.domain.usecases.CreateUserUseCase
import org.my.firstcircletest.domain.usecases.GetUserTransactionsError
import org.my.firstcircletest.domain.usecases.GetUserTransactionsUseCase
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
@Validated
class UserController(
    private val createUserUseCase: CreateUserUseCase,
    private val getUserTransactionsUseCase: GetUserTransactionsUseCase,
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @PostMapping
    suspend fun createUser(
        @Valid @RequestBody requestDto: CreateUserRequestDto
    ): ResponseEntity<*> {
        logger.info("Creating user with name: ${requestDto.name}")

        return createUserUseCase.invoke(requestDto.toDomain()).fold(
            ifLeft = { error ->
                logger.error("Failed to create user: ${error.message}")
                handleCreateUserError(error)
            },
            ifRight = { user ->
                logger.info("User created successfully: ${user.id}")
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(CreateUserResponseDto.fromDomain(user))
            }
        )
    }

    @GetMapping("/{userId}/transactions")
    suspend fun getUserTransactions(
        @PathVariable @ValidUserId userId: String
    ): ResponseEntity<*> {
        logger.info("Getting transactions for user: $userId")

        return getUserTransactionsUseCase.invoke(userId).fold(
            ifLeft = { error ->
                logger.error("Failed to get transactions for user $userId: ${error.message}")
                handleGetUserTransactionsError(error)
            },
            ifRight = { transactions ->
                logger.info("Retrieved ${transactions.size} transactions for user: $userId")
                ResponseEntity.ok(GetUserTransactionsResponseDto.fromDomain(userId, transactions))
            }
        )
    }

    private fun handleCreateUserError(error: CreateUserError): ResponseEntity<ErrorResponseDto> {
        return when (error) {
            is CreateUserError.UserCreationFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "USER_CREATION_FAILED",
                    message = error.message
                ))

            is CreateUserError.WalletCreationFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "WALLET_CREATION_FAILED",
                    message = error.message
                ))
        }
    }

    private fun handleGetUserTransactionsError(error: GetUserTransactionsError): ResponseEntity<ErrorResponseDto> {
        return when (error) {
            is GetUserTransactionsError.InvalidUserId -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = error.message
                ))

            is GetUserTransactionsError.TransactionRetrievalFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "TRANSACTION_RETRIEVAL_FAILED",
                    message = error.message
                ))
        }
    }
}
