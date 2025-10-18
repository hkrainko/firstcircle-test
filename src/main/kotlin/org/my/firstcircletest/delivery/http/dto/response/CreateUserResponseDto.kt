package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.User

data class CreateUserResponseDto(
    @field:JsonProperty("user_id")
    val userId: String,

    @field:JsonProperty("name")
    val name: String
) {
    companion object {
        fun fromDomain(user: User): CreateUserResponseDto {
            return CreateUserResponseDto(
                userId = user.id,
                name = user.name
            )
        }
    }
}
