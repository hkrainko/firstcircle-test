package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.Wallet

data class WithdrawResponseDto(
    @JsonProperty("wallet_id")
    val walletId: String,

    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("balance")
    val balance: Int
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
