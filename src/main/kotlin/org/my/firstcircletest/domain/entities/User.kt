package org.my.firstcircletest.domain.entities

import java.util.UUID

typealias UserID = UUID

data class User(
    val id: UserID,
    val name: String
)
