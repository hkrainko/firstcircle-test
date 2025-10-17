package org.my.firstcircletest.data.repositories.postgres.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.my.firstcircletest.domain.entities.Wallet

@Entity
@Table(name = "wallets")
class WalletEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "balance", nullable = false)
    var balance: Int = 0
) {
    companion object {
        fun fromDomain(wallet: Wallet): WalletEntity {
            return WalletEntity(
                id = wallet.id,
                userId = wallet.userId,
                balance = wallet.balance
            )
        }
    }

    fun toDomain(): Wallet {
        return Wallet(
            id = id,
            userId = userId,
            balance = balance
        )
    }
}
