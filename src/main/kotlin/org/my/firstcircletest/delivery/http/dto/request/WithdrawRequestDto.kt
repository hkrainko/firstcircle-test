package org.my.firstcircletest.delivery.http.dto.request

import org.my.firstcircletest.delivery.http.validation.ValidAmount

data class WithdrawRequestDto(
    @field:ValidAmount
    val amount: Int
)
