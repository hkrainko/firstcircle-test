package org.my.firstcircletest.domain.entities.errors

sealed class DomainError(message: String) : Exception(message) {
    data class UserNotFoundException(val errorMessage: String = "user not found") : DomainError(errorMessage)
    data class WalletNotFoundException(val errorMessage: String = "wallet not found") : DomainError(errorMessage)
    data class DatabaseException(val errorMessage: String = "database error") : DomainError(errorMessage)
    data class InvalidRequestException(val errorMessage: String = "invalid request") : DomainError(errorMessage)
    data class InvalidUserIdException(val errorMessage: String = "invalid user ID") : DomainError(errorMessage)
    data class InvalidTransactionTypeException(val errorMessage: String = "invalid transaction type") : DomainError(errorMessage)
    data class InvalidTransactionStatusException(val errorMessage: String = "invalid transaction status") : DomainError(errorMessage)
    data class NonPositiveAmountException(val errorMessage: String = "amount must be greater than zero") : DomainError(errorMessage)
    data class InsufficientBalanceException(val errorMessage: String = "insufficient balance") : DomainError(errorMessage)
    data class SameUserTransferException(val errorMessage: String = "cannot transfer to the same user") : DomainError(errorMessage)
    data class InvalidUserNameException(val errorMessage: String = "invalid user name") : DomainError(errorMessage)
}
