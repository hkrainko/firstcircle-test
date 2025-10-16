package org.my.firstcircletest.domain.entities

typealias UserID = String

data class User(
    val id: UserID,
    val name: String
)
