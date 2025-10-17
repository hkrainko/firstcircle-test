package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.WalletID

interface WalletRepository {
    fun getWalletByUserId(userId: String): Either<RepositoryError, Wallet>
    fun createWallet(request: CreateWalletRequest): Either<RepositoryError, Wallet>
    fun updateWalletBalance(walletId: String, balance: Int): Either<RepositoryError, Wallet>
}
