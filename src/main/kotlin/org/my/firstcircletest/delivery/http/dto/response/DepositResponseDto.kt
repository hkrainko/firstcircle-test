package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.Wallet

data class DepositResponseDto(
    @JsonProperty("wallet_id")
    val walletId: String,

    @JsonProperty("user_id")
    val userId: String,

    @JsonProperty("amount")
    val amount: Int
) {
    companion object {
        fun fromDomain(wallet: Wallet): DepositResponseDto {
            return DepositResponseDto(
                walletId = wallet.id,
                userId = wallet.userId,
                amount = wallet.balance
            )
        }
    }
}
