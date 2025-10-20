package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.CreateUserResponse

data class CreateUserResponseDto(
    @field:JsonProperty("user_id")
    val userId: String,

    @field:JsonProperty("name")
    val name: String,

    @field:JsonProperty("wallet_id")
    val walletId: String,

    @field:JsonProperty("balance")
    val balance: Long
) {
    companion object {
        fun fromDomain(response: CreateUserResponse): CreateUserResponseDto {
            return CreateUserResponseDto(
                userId = response.user.id,
                name = response.user.name,
                walletId = response.wallet.id,
                balance = response.wallet.balance
            )
        }
    }
}
