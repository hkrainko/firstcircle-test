package org.my.firstcircletest.domain.entities

data class Transfer(
    val fromUserId: UserID,
    val toUserId: UserID,
    val amount: Int
)
