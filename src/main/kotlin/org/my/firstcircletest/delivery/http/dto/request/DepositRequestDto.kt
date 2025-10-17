package org.my.firstcircletest.delivery.http.dto.request

import jakarta.validation.constraints.Positive

data class DepositRequestDto(
    @field:Positive(message = "Amount must be positive")
    val amount: Int
)
