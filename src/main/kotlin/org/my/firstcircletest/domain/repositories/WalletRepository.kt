package org.my.firstcircletest.domain.repositories

import arrow.core.Either
import org.my.firstcircletest.domain.entities.CreateWalletRequest
import org.my.firstcircletest.domain.entities.UserID
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.entities.WalletID

interface WalletRepository {
    suspend fun getWalletByUserId(userId: String): Either<RepositoryError, Wallet>
    suspend fun createWallet(request: CreateWalletRequest): Either<RepositoryError, Wallet>
    suspend fun updateWalletBalance(walletId: String, balance: Long): Either<RepositoryError, Wallet>
}
