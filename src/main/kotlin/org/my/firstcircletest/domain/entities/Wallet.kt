package org.my.firstcircletest.domain.entities

typealias WalletID = String

data class Wallet(
    val id: WalletID,
    val userId: UserID,
    val balance: Long
)
