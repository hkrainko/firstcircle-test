package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.Wallet

data class WithdrawResponseDto(
    @field:JsonProperty("wallet_id")
    val walletId: String,

    @field:JsonProperty("user_id")
    val userId: String,

    @field:JsonProperty("balance")
    val balance: Long
) {
    companion object {
        fun fromDomain(wallet: Wallet): WithdrawResponseDto {
            return WithdrawResponseDto(
                walletId = wallet.id,
                userId = wallet.userId,
                balance = wallet.balance
            )
        }
    }
}
