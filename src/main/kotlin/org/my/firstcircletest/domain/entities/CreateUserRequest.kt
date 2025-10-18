package org.my.firstcircletest.domain.entities

data class CreateUserRequest(
    val name: String,
    val initBalance: Int
)
