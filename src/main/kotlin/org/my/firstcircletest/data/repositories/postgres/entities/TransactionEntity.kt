package org.my.firstcircletest.data.repositories.postgres.entities

import org.springframework.data.domain.Persistable
import org.springframework.data.annotation.Transient
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.my.firstcircletest.domain.entities.Transaction
import org.my.firstcircletest.domain.entities.TransactionStatus
import org.my.firstcircletest.domain.entities.TransactionType
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.slf4j.LoggerFactory
import org.springframework.data.relational.core.mapping.Column
import java.time.LocalDateTime

@Table("transactions")
data class TransactionEntity(
    @Id
    @Column("id")
    @get:JvmName("getEntityId")
    val id: String,

    @Column("wallet_id")
    val walletId: String,

    @Column("user_id")
    val userId: String,

    @Column("destination_wallet_id")
    val destinationWalletId: String? = null,

    @Column("destination_user_id")
    val destinationUserId: String? = null,

    @Column("amount")
    val amount: Long,

    @Column("type")
    val type: String,

    @Column("created_at")
    val createdAt: LocalDateTime,

    @Column("updated_at")
    val updatedAt: LocalDateTime,

    @Column("status")
    val status: String,

) : Persistable<String> {

    @Transient
    private var _isNew: Boolean = false

    override fun getId(): String = id

    @Transient
    override fun isNew(): Boolean = _isNew

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionEntity::class.java)

        fun newTransactionEntity(
            walletId: String,
            userId: String,
            destinationWalletId: String?,
            destinationUserId: String?,
            amount: Long,
            type: TransactionType,
            createdAt: LocalDateTime,
            status: TransactionStatus,
        ): TransactionEntity {
            val id = "txn-${java.util.UUID.randomUUID()}"
            return TransactionEntity(
                id = id,
                walletId = walletId,
                userId = userId,
                destinationWalletId = destinationWalletId,
                destinationUserId = destinationUserId,
                amount = amount,
                type = type.name,
                createdAt = createdAt,
                updatedAt = createdAt,
                status = status.name
            ).apply { _isNew = true }
        }

        fun newFromDomain(transaction: Transaction): TransactionEntity {
            return TransactionEntity(
                id = transaction.id,
                walletId = transaction.walletId,
                userId = transaction.userId,
                destinationWalletId = transaction.destinationWalletId,
                destinationUserId = transaction.destinationUserId,
                amount = transaction.amount,
                type = transaction.type.name,
                createdAt = transaction.createdAt,
                updatedAt = transaction.createdAt,
                status = transaction.status.name,
            ).apply { _isNew = true }
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
}
