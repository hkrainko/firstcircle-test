package org.my.firstcircletest.data.repositories.postgres.dto

import jakarta.persistence.*
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.entities.errors.DomainError
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
class TransactionDTO(
    @Id
    @Column(name = "id", nullable = false)
    var id: String = "",

    @Column(name = "wallet_id", nullable = false)
    var walletId: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "destination_wallet_id")
    var destinationWalletId: String? = null,

    @Column(name = "destination_user_id")
    var destinationUserId: String? = null,

    @Column(name = "amount", nullable = false)
    var amount: Int = 0,

    @Column(name = "type", nullable = false)
    var type: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "status", nullable = false)
    var status: String = ""
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TransactionDTO::class.java)

        fun fromDomain(transaction: Transaction): TransactionDTO {
            return TransactionDTO(
                id = transaction.id.toString(),
                walletId = transaction.walletId.toString(),
                userId = transaction.userId.toString(),
                destinationWalletId = transaction.destinationWalletId?.toString(),
                destinationUserId = transaction.destinationUserId?.toString(),
                amount = transaction.amount,
                type = transaction.type.name,
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt ?: LocalDateTime.now(),
                status = transaction.status.name
            )
        }
    }

    fun toDomain(): Transaction {
        val transactionType = try {
            TransactionType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            logger.error("TransactionDTO.toDomain: error converting type: {}", type, e)
            throw DomainError.InvalidTransactionTypeException("Invalid transaction type: $type")
        }

        val transactionStatus = try {
            TransactionStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            logger.error("TransactionDTO.toDomain: error converting status: {}", status, e)
            throw DomainError.InvalidTransactionStatusException("Invalid transaction status: $status")
        }

        return Transaction(
            id = id,
            walletId = walletId,
            userId = userId,
            destinationWalletId = destinationWalletId,
            destinationUserId = destinationUserId,
            amount = amount,
            type = transactionType,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = transactionStatus
        )
    }

    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
