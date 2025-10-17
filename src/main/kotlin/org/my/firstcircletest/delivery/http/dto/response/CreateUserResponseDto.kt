package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.User

data class CreateUserResponseDto(
    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("name")
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
