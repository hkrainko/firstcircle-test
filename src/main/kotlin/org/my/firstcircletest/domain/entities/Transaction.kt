package org.my.firstcircletest.domain.entities

import java.time.LocalDateTime
import java.util.*


typealias TransactionID = String

data class Transaction(
    val id: TransactionID,
    val walletId: WalletID,
    val userId: UserID,
    val destinationWalletId: WalletID? = null,
    val destinationUserId: UserID? = null,
    val amount: Long,
    val type: TransactionType,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,
    val status: TransactionStatus
) {
    companion object {
        fun newTransaction(
            walletId: WalletID,
            userId: UserID,
            type: TransactionType,
            amount: Long,
            status: TransactionStatus
        ): Transaction {
            return Transaction(
                id = generateTransactionID(),
                walletId = walletId,
                userId = userId,
                type = type,
                amount = amount,
                createdAt = LocalDateTime.now(),
                status = status
            )
        }

        fun newTransferTransaction(
            sourceWalletId: WalletID,
            userId: UserID,
            destinationWalletId: WalletID,
            destinationUserId: UserID,
            amount: Long,
            status: TransactionStatus
        ): Transaction {
            return Transaction(
                id = generateTransactionID(),
                walletId = sourceWalletId,
                userId = userId,
                destinationWalletId = destinationWalletId,
                destinationUserId = destinationUserId,
                type = TransactionType.TRANSFER,
                amount = amount,
                createdAt = LocalDateTime.now(),
                status = status
            )
        }

        private fun generateTransactionID(): TransactionID {
            return UUID.randomUUID().toString()
        }
    }
}
