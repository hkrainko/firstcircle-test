package org.my.firstcircletest.domain.entities

import java.util.UUID

typealias WalletID = UUID

data class Wallet(
    val id: WalletID,
    val userId: UserID,
    val balance: Int
)
