package org.my.firstcircletest.delivery.http.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.my.firstcircletest.delivery.http.validation.ValidUserName
import org.my.firstcircletest.domain.entities.CreateUserRequest

data class CreateUserRequestDto(
    @field:ValidUserName
    val name: String,

    @field:JsonProperty("init_balance")
    @field:Min(value = 0, message = "Initial balance cannot be negative")
    @field:Max(100_000_000, message = "Requested initial balance exceeds the maximum limit")
    val initBalance: Long
) {
    fun toDomain() = CreateUserRequest(
        name = this.name,
        initBalance = this.initBalance
    )
}
