package org.my.firstcircletest.domain.entities

data class CreateWalletRequest(
    val userId: UserID,
    val balance: Int
)
