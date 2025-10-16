package org.my.firstcircletest.domain.entities

import java.time.LocalDateTime
import java.util.UUID


typealias TransactionID = UUID

data class Transaction(
    val id: TransactionID,
    val walletId: WalletID,
    val userId: UserID,
    val destinationWalletId: WalletID? = null,
    val destinationUserId: UserID? = null,
    val amount: Int,
    val type: TransactionType,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,
    val status: TransactionStatus
) {
    companion object {
        fun newTransaction(
            walletId: WalletID,
            userId: UserID,
            transactionType: TransactionType,
            amount: Int,
            status: TransactionStatus
        ): Transaction {
            return Transaction(
                id = generateTransactionID(),
                walletId = walletId,
                userId = userId,
                type = transactionType,
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
            amount: Int,
            status: TransactionStatus
        ): Transaction {
            return Transaction(
                id = generateTransactionID(),
                walletId = sourceWalletId,
                userId = userId,
                destinationWalletId = destinationWalletId,
                destinationUserId = destinationUserId,
                type = TransactionType.Transfer,
                amount = amount,
                createdAt = LocalDateTime.now(),
                status = status
            )
        }

        private fun generateTransactionID(): TransactionID {
            return UUID.randomUUID()
        }
    }
}
