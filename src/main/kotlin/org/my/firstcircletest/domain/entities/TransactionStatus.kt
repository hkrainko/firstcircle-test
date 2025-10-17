package org.my.firstcircletest.domain.entities

enum class TransactionStatus(val value: String) {
    COMPLETED("COMPLETED"),
    PENDING_CANCEL("PENDING_CANCEL"),
    CANCELLED("CANCELLED");

    override fun toString(): String = value

    companion object {
        fun fromString(s: String): TransactionStatus {
            return when (s) {
                "COMPLETED" -> COMPLETED
                "PENDING_CANCEL" -> PENDING_CANCEL
                "CANCELLED" -> CANCELLED
                else -> throw IllegalArgumentException("invalid transaction status: $s")
            }
        }
    }
}
