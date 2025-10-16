package org.my.firstcircletest.data.repositories.postgres.dto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.my.firstcircletest.domain.entities.User

@Entity
@Table(name = "users")
class UserDTO(
    @Id
    @Column(name = "id", nullable = false)
    var id: String,

    @Column(name = "name", nullable = false)
    var name: String = ""
) {
    companion object {
        fun fromDomain(user: User): UserDTO {
            return UserDTO(
                id = user.id,
                name = user.name
            )
        }
    }

    fun toDomain(): User {
        return User(
            id = id,
            name = name
        )
    }
}