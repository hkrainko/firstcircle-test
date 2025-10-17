package org.my.firstcircletest.delivery.http.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class TransferRequestDto(
    @field:NotBlank(message = "To user ID cannot be blank")
    val toUserId: String,

    @field:Positive(message = "Amount must be positive")
    val amount: Int
)
