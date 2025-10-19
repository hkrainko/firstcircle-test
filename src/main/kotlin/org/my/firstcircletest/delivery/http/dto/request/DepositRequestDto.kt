package org.my.firstcircletest.delivery.http.dto.request

import org.my.firstcircletest.delivery.http.validation.ValidAmount

data class DepositRequestDto(
    @field:ValidAmount
    val amount: Long
)
