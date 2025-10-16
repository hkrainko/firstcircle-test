package org.my.firstcircletest.domain.entities

enum class TransactionStatus(val value: String) {
    Completed("completed"),
    PendingCancel("pending_cancel"),
    Cancelled("cancelled");

    override fun toString(): String = value

    companion object {
        fun fromString(s: String): TransactionStatus {
            return when (s) {
                "completed" -> Completed
                "pending_cancel" -> PendingCancel
                "cancelled" -> Cancelled
                else -> throw IllegalArgumentException("invalid transaction status: $s")
            }
        }
    }
}
