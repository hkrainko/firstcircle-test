package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.WalletID
import org.my.firstcircletest.domain.entities.errors.DomainError

interface WalletRepository {
    fun getWalletByUserId(userId: String): Either<DomainError, Wallet>
    fun createWallet(request: CreateWalletRequest): Either<DomainError, Wallet>
    fun updateWalletBalance(walletId: String, balance: Int): Either<DomainError, Wallet>
}
