package org.my.firstcircletest.domain.repositories

import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.WalletID

interface WalletRepository {
    fun getWalletByUserId(userId: UserID): Wallet?
    fun createWallet(request: CreateWalletRequest): Wallet
    fun updateWalletBalance(walletId: WalletID, balance: Int): Wallet
}
