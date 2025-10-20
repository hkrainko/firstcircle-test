package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.my.firstcircletest.domain.entities.Wallet

data class DepositResponseDto(
    @field:JsonProperty("wallet_id")
    val walletId: String,

    @field:JsonProperty("user_id")
    val userId: String,

    @field:JsonProperty("amount")
    val amount: Long
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
