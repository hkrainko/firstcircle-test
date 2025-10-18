package org.my.firstcircletest.delivery.http.dto.request

import org.my.firstcircletest.delivery.http.validation.ValidAmount
import org.my.firstcircletest.delivery.http.validation.ValidUserId

data class TransferRequestDto(
    @field:ValidUserId
    val toUserId: String,

    @field:ValidAmount
    val amount: Int
)
