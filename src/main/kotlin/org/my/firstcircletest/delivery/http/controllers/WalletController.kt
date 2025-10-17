package org.my.firstcircletest.delivery.http.controllers

import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.my.firstcircletest.delivery.http.dto.request.DepositRequestDto
import org.my.firstcircletest.delivery.http.dto.request.TransferRequestDto
import org.my.firstcircletest.delivery.http.dto.request.WithdrawRequestDto
import org.my.firstcircletest.delivery.http.dto.response.*
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.usecases.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/wallets")
@Validated
class WalletController(
    private val getWalletInfoUseCase: GetWalletInfoUseCase,
    private val depositUseCase: DepositUseCase,
    private val withdrawUseCase: WithdrawUseCase,
    private val transferUseCase: TransferUseCase
) {

    private val logger = LoggerFactory.getLogger(WalletController::class.java)

    @GetMapping("/{userId}")
    fun getWalletInfo(
        @PathVariable userId: String
    ): ResponseEntity<*> = runBlocking {
        logger.info("Getting wallet info for user: $userId")

        if (userId.isBlank()) {
            return@runBlocking ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = "User ID cannot be blank"
                ))
        }

        getWalletInfoUseCase.invoke(userId).fold(
            ifLeft = { error ->
                logger.error("Failed to get wallet info for user $userId: ${error.message}")
                handleGetWalletInfoError(error)
            },
            ifRight = { wallet ->
                logger.info("Retrieved wallet info for user: $userId")
                ResponseEntity.ok(WalletInfoResponseDto.fromDomain(wallet))
            }
        )
    }

    @PostMapping("/{userId}/deposit")
    fun deposit(
        @PathVariable userId: String,
        @Valid @RequestBody requestDto: DepositRequestDto
    ): ResponseEntity<*> = runBlocking {
        logger.info("Depositing ${requestDto.amount} for user: $userId")

        if (userId.isBlank()) {
            return@runBlocking ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = "User ID cannot be blank"
                ))
        }

        depositUseCase.invoke(userId, requestDto.amount).fold(
            ifLeft = { error ->
                logger.error("Failed to deposit for user $userId: ${error.message}")
                handleDepositError(error)
            },
            ifRight = { wallet ->
                logger.info("Deposit successful for user: $userId")
                ResponseEntity.ok(DepositResponseDto.fromDomain(wallet))
            }
        )
    }

    @PostMapping("/{userId}/withdraw")
    fun withdraw(
        @PathVariable userId: String,
        @Valid @RequestBody requestDto: WithdrawRequestDto
    ): ResponseEntity<*> = runBlocking {
        logger.info("Withdrawing ${requestDto.amount} for user: $userId")

        if (userId.isBlank()) {
            return@runBlocking ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = "User ID cannot be blank"
                ))
        }

        withdrawUseCase.invoke(userId, requestDto.amount).fold(
            ifLeft = { error ->
                logger.error("Failed to withdraw for user $userId: ${error.message}")
                handleWithdrawError(error)
            },
            ifRight = { wallet ->
                logger.info("Withdrawal successful for user: $userId")
                ResponseEntity.ok(WithdrawResponseDto.fromDomain(wallet))
            }
        )
    }

    @PostMapping("/{userId}/transfer")
    fun transfer(
        @PathVariable userId: String,
        @Valid @RequestBody requestDto: TransferRequestDto
    ): ResponseEntity<*> = runBlocking {
        logger.info("Transferring ${requestDto.amount} from user $userId to user ${requestDto.toUserId}")

        if (userId.isBlank()) {
            return@runBlocking ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = "User ID cannot be blank"
                ))
        }

        val transfer = Transfer(
            fromUserId = userId,
            toUserId = requestDto.toUserId,
            amount = requestDto.amount
        )

        transferUseCase.invoke(transfer).fold(
            ifLeft = { error ->
                logger.error("Failed to transfer from user $userId to ${requestDto.toUserId}: ${error.message}")
                handleTransferError(error)
            },
            ifRight = { completedTransfer ->
                logger.info("Transfer successful from user $userId to ${requestDto.toUserId}")
                ResponseEntity.ok(TransferResponseDto.fromDomain(completedTransfer))
            }
        )
    }

    private fun handleGetWalletInfoError(error: GetWalletInfoError): ResponseEntity<ErrorResponseDto> {
        return when (error) {
            is GetWalletInfoError.InvalidUserId -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = error.message
                ))

            is GetWalletInfoError.WalletNotFound -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto(
                    error = "WALLET_NOT_FOUND",
                    message = error.message
                ))
        }
    }

    private fun handleDepositError(error: DepositError): ResponseEntity<ErrorResponseDto> {
        return when (error) {
            is DepositError.InvalidUserId -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = error.message
                ))

            is DepositError.NonPositiveAmount -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "NON_POSITIVE_AMOUNT",
                    message = error.message
                ))

            is DepositError.WalletNotFound -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto(
                    error = "WALLET_NOT_FOUND",
                    message = error.message
                ))

            is DepositError.TransactionCreationFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "TRANSACTION_CREATION_FAILED",
                    message = error.message
                ))

            is DepositError.WalletUpdateFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "WALLET_UPDATE_FAILED",
                    message = error.message
                ))
        }
    }

    private fun handleWithdrawError(error: WithdrawError): ResponseEntity<ErrorResponseDto> {
        return when (error) {
            is WithdrawError.InvalidUserId -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = error.message
                ))

            is WithdrawError.NonPositiveAmount -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "NON_POSITIVE_AMOUNT",
                    message = error.message
                ))

            is WithdrawError.WalletNotFound -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto(
                    error = "WALLET_NOT_FOUND",
                    message = error.message
                ))

            is WithdrawError.InsufficientBalance -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INSUFFICIENT_BALANCE",
                    message = error.message
                ))

            is WithdrawError.TransactionCreationFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "TRANSACTION_CREATION_FAILED",
                    message = error.message
                ))

            is WithdrawError.WalletUpdateFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "WALLET_UPDATE_FAILED",
                    message = error.message
                ))
        }
    }

    private fun handleTransferError(error: TransferError): ResponseEntity<ErrorResponseDto> {
        return when (error) {
            is TransferError.InvalidUserId -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INVALID_USER_ID",
                    message = error.message
                ))

            is TransferError.NonPositiveAmount -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "NON_POSITIVE_AMOUNT",
                    message = error.message
                ))

            is TransferError.SameUserTransfer -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "SAME_USER_TRANSFER",
                    message = error.message
                ))

            is TransferError.WalletNotFound -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto(
                    error = "WALLET_NOT_FOUND",
                    message = error.message
                ))

            is TransferError.InsufficientBalance -> ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto(
                    error = "INSUFFICIENT_BALANCE",
                    message = error.message
                ))

            is TransferError.WalletUpdateFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "WALLET_UPDATE_FAILED",
                    message = error.message
                ))

            is TransferError.TransactionCreationFailed -> ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto(
                    error = "TRANSACTION_CREATION_FAILED",
                    message = error.message
                ))
        }
    }
}
