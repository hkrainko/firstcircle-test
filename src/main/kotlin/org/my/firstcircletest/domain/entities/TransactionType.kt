package org.my.firstcircletest.domain.entities

enum class TransactionType(val value: String) {
    DEPOSIT("DEPOSIT"),
    WITHDRAWAL("WITHDRAWAL"),
    TRANSFER("TRANSFER");

    override fun toString(): String = value

    companion object {
        fun fromString(s: String): TransactionType {
            return when (s) {
                "DEPOSIT" -> DEPOSIT
                "WITHDRAWAL" -> WITHDRAWAL
                "TRANSFER" -> TRANSFER
                else -> throw IllegalArgumentException("invalid transaction type: $s")
            }
        }
    }
}
