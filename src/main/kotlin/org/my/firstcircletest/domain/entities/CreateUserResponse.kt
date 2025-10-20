package org.my.firstcircletest.domain.entities

data class CreateUserResponse(
    val user: User,
    val wallet: Wallet
)