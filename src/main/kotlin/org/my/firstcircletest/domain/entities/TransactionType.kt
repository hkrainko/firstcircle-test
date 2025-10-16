package org.my.firstcircletest.domain.entities

enum class TransactionType(val value: String) {
    Deposit("deposit"),
    Withdrawal("withdrawal"),
    Transfer("transfer");

    override fun toString(): String = value

    companion object {
        fun fromString(s: String): TransactionType {
            return when (s) {
                "deposit" -> Deposit
                "withdrawal" -> Withdrawal
                "transfer" -> Transfer
                else -> throw IllegalArgumentException("invalid transaction type: $s")
            }
        }
    }
}
