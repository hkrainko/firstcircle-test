package org.my.firstcircletest.data.repositories.postgres.entities

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.my.firstcircletest.domain.entities.Wallet
import java.util.UUID

@Table("wallets")
data class WalletEntity(
    @Id
    @Column("id")
    @get:JvmName("getEntityId")
    var id: String = "",

    @Column("user_id")
    var userId: String = "",

    @Column("balance")
    var balance: Long = 0,

) : Persistable<String> {

    @Transient
    private var _isNew: Boolean = false

    override fun getId(): String = id

    @Transient
    override fun isNew(): Boolean = _isNew

    companion object {
        fun newWallet(userId: String, balance: Long): WalletEntity {
            val walletId = "wallet-${UUID.randomUUID()}"

            return WalletEntity(
                id = walletId,
                userId = userId,
                balance = balance,
            ).apply { _isNew = true}
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
