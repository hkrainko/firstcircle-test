package org.my.firstcircletest.data.repositories.postgres.dto

import jakarta.persistence.*
import org.my.firstcircletest.domain.entities.User
import java.util.UUID

@Entity
@Table(name = "users")
data class UserDTO(
    @Id
    @Column(name = "id", nullable = false)
    var id: String,

    @Column(name = "name", nullable = false)
    var name: String = ""
) {
    companion object {
        fun fromDomain(user: User): UserDTO {
            return UserDTO(
                id = user.id.toString(),
                name = user.name
            )
        }
    }

    fun toDomain(): User {
        return User(
            id = UUID.fromString(id),
            name = name
        )
    }
}