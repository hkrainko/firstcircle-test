package org.my.firstcircletest.data.repositories.postgres.entities

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import jakarta.persistence.*
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
class TransactionEntity(
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
        private val logger = LoggerFactory.getLogger(TransactionEntity::class.java)

        fun fromDomain(transaction: Transaction): TransactionEntity {
            return TransactionEntity(
                id = transaction.id,
                walletId = transaction.walletId,
                userId = transaction.userId,
                destinationWalletId = transaction.destinationWalletId,
                destinationUserId = transaction.destinationUserId,
                amount = transaction.amount,
                type = transaction.type.name,
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt ?: LocalDateTime.now(),
                status = transaction.status.name
            )
        }
    }

    fun toDomain(): Either<RepositoryError, Transaction> {
        val transactionType = try {
            TransactionType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            logger.error("TransactionDTO.toDomain: error converting type: {}", type, e)
            return RepositoryError.ConversionFailed("Invalid transaction type: $type").left()
        }

        val transactionStatus = try {
            TransactionStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            logger.error("TransactionDTO.toDomain: error converting status: {}", status, e)
            return RepositoryError.ConversionFailed("Invalid transaction status: $status").left()
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
        ).right()
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
