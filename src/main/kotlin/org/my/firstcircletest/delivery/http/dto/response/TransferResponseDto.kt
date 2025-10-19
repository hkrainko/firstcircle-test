package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.Transfer

data class TransferResponseDto(
    @JsonProperty("from_user_id")
    val fromUserId: String,

    @JsonProperty("to_user_id")
    val toUserId: String,

    @JsonProperty("amount")
    val amount: Long
) {
    companion object {
        fun fromDomain(transfer: Transfer): TransferResponseDto {
            return TransferResponseDto(
                fromUserId = transfer.fromUserId,
                toUserId = transfer.toUserId,
                amount = transfer.amount
            )
        }
    }
}
