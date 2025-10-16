package org.my.firstcircletest.data.repositories.postgres.dto

import jakarta.persistence.*
import org.my.firstcircletest.domain.entities.Wallet
import java.util.UUID

@Entity
@Table(name = "wallets")
data class WalletDTO(
    @Id
    @Column(name = "id", nullable = false)
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "balance", nullable = false)
    var balance: Long = 0
) {
    companion object {
        fun fromDomain(wallet: Wallet): WalletDTO {
            return WalletDTO(
                id = wallet.id.toString(),
                userId = wallet.userId.toString(),
                balance = wallet.balance.toLong()
            )
        }
    }

    fun toDomain(): Wallet {
        return Wallet(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            balance = balance.toInt()
        )
    }
}
