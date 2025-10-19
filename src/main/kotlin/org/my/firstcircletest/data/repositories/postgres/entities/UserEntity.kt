package org.my.firstcircletest.data.repositories.postgres.entities

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.my.firstcircletest.domain.entities.User
import java.util.UUID

@Table("users")
data class UserEntity(
    @Id
    @Column("id")
    @get:JvmName("getEntityId")
    var id: String,

    @Column("name")
    var name: String = "",

) : Persistable<String> {

    @Transient
    private var _isNew: Boolean = false

    override fun getId(): String = id

    @Transient
    override fun isNew(): Boolean = _isNew

    companion object {

        fun newUser(name: String): UserEntity {
            return UserEntity(
                id = "user-${UUID.randomUUID()}",
                name = name
            ).apply { _isNew = true }
        }

        fun fromDomain(user: User): UserEntity {
            return UserEntity(
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