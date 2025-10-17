package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.Transaction
import java.time.format.DateTimeFormatter

data class GetUserTransactionsResponseDto(
    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("transactions")
    val transactions: List<TransactionDto>
) {
    companion object {
        fun fromDomain(userId: String, transactions: List<Transaction>): GetUserTransactionsResponseDto {
            return GetUserTransactionsResponseDto(
                userId = userId,
                transactions = transactions.map { TransactionDto.fromDomain(it) }
            )
        }
    }
}

data class TransactionDto(
    @JsonProperty("transaction_id")
    val transactionId: String,

    @JsonProperty("wallet_id")
    val walletId: String,

    @JsonProperty("destination_wallet_id")
    val destinationWalletId: String? = null,

    @JsonProperty("amount")
    val amount: Int,

    @JsonProperty("type")
    val type: String,

    @JsonProperty("created_at")
    val createdAt: String,

    @JsonProperty("updated_at")
    val updatedAt: String?,

    @JsonProperty("state")
    val state: String
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun fromDomain(transaction: Transaction): TransactionDto {
            return TransactionDto(
                transactionId = transaction.id,
                walletId = transaction.walletId,
                destinationWalletId = transaction.destinationWalletId,
                amount = transaction.amount,
                type = transaction.type.toString(),
                createdAt = transaction.createdAt.format(formatter),
                updatedAt = transaction.updatedAt?.format(formatter),
                state = transaction.status.toString()
            )
        }
    }
}
