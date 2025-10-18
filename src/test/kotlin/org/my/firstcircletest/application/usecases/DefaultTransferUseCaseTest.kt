package org.my.firstcircletest.application.usecases

import arrow.core.left
import arrow.core.right
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.my.firstcircletest.domain.entities.Transfer
import org.my.firstcircletest.domain.entities.Wallet
import org.my.firstcircletest.domain.repositories.RepositoryError
import org.my.firstcircletest.domain.repositories.TransactionRepository
import org.my.firstcircletest.domain.repositories.WalletRepository
import org.my.firstcircletest.domain.usecases.TransferError
import org.springframework.transaction.interceptor.TransactionAspectSupport

class DefaultTransferUseCaseTest {

    private val walletRepository: WalletRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val useCase = DefaultTransferUseCase(walletRepository, transactionRepository)

    @BeforeEach
    fun setup() {
        mockkStatic(TransactionAspectSupport::class)
        every { TransactionAspectSupport.currentTransactionStatus() } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(TransactionAspectSupport::class)
    }

    @Test
    fun `should successfully transfer amount between wallets`() = runTest {
        // Given
        val fromUserId = "user123"
        val toUserId = "user456"
        val amount = 500
        val transfer = Transfer(fromUserId = fromUserId, toUserId = toUserId, amount = amount)

        val fromWallet = Wallet(id = "wallet123", userId = fromUserId, balance = 1000)
        val toWallet = Wallet(id = "wallet456", userId = toUserId, balance = 500)
        val updatedFromWallet = fromWallet.copy(balance = 500)
        val updatedToWallet = toWallet.copy(balance = 1000)

        coEvery { walletRepository.getWalletByUserId(fromUserId) } returns fromWallet.right()
        coEvery { walletRepository.updateWalletBalance(fromWallet.id, 500) } returns updatedFromWallet.right()
        coEvery { walletRepository.getWalletByUserId(toUserId) } returns toWallet.right()
        coEvery { walletRepository.updateWalletBalance(toWallet.id, 1000) } returns updatedToWallet.right()
        coEvery { transactionRepository.create(any()) } returns mockk<org.my.firstcircletest.domain.entities.Transaction>().right()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isRight())
        result.fold(
            ifLeft = { },
            ifRight = { resultTransfer ->
                assertEquals(fromUserId, resultTransfer.fromUserId)
                assertEquals(toUserId, resultTransfer.toUserId)
                assertEquals(amount, resultTransfer.amount)
            }
        )

        coVerify { walletRepository.getWalletByUserId(fromUserId) }
        coVerify { walletRepository.getWalletByUserId(toUserId) }
        coVerify { walletRepository.updateWalletBalance(fromWallet.id, 500) }
        coVerify { walletRepository.updateWalletBalance(toWallet.id, 1000) }
        coVerify { transactionRepository.create(any()) }
    }

    @Test
    fun `should return InvalidUserId when fromUserId is blank`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "", toUserId = "user456", amount = 500)

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.InvalidUserId)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.getWalletByUserId(any()) }
    }

    @Test
    fun `should return InvalidUserId when toUserId is blank`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "", amount = 500)

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.InvalidUserId)
            },
            ifRight = { }
        )
    }

    @Test
    fun `should return NonPositiveAmount when amount is zero`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 0)

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.NonPositiveAmount)
            },
            ifRight = { }
        )
    }

    @Test
    fun `should return NonPositiveAmount when amount is negative`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = -100)

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.NonPositiveAmount)
            },
            ifRight = { }
        )
    }

    @Test
    fun `should return SameUserTransfer when transferring to same user`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user123", amount = 500)

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.SameUserTransfer)
            },
            ifRight = { }
        )

        coVerify(exactly = 0) { walletRepository.getWalletByUserId(any()) }
    }

    @Test
    fun `should return WalletNotFound when source wallet does not exist`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 500)
        val repositoryError = RepositoryError.NotFound("Wallet not found")

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns repositoryError.left()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.WalletNotFound)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(transfer.fromUserId) }
        coVerify(exactly = 0) { walletRepository.updateWalletBalance(any(), any()) }
    }

    @Test
    fun `should return InsufficientBalance when source wallet has insufficient balance`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 1500)
        val fromWallet = Wallet(id = "wallet123", userId = transfer.fromUserId, balance = 1000)

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns fromWallet.right()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.InsufficientBalance)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(transfer.fromUserId) }
        coVerify(exactly = 0) { walletRepository.updateWalletBalance(any(), any()) }
    }

    @Test
    fun `should return WalletUpdateFailed when source wallet update fails`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 500)
        val fromWallet = Wallet(id = "wallet123", userId = transfer.fromUserId, balance = 1000)
        val repositoryError = RepositoryError.DatabaseError("Update failed")

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns fromWallet.right()
        coEvery { walletRepository.updateWalletBalance(fromWallet.id, 500) } returns repositoryError.left()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.WalletUpdateFailed)
            },
            ifRight = { }
        )

        coVerify { walletRepository.updateWalletBalance(fromWallet.id, 500) }
        coVerify(exactly = 0) { walletRepository.getWalletByUserId(transfer.toUserId) }
    }

    @Test
    fun `should return WalletNotFound when destination wallet does not exist`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 500)
        val fromWallet = Wallet(id = "wallet123", userId = transfer.fromUserId, balance = 1000)
        val updatedFromWallet = fromWallet.copy(balance = 500)
        val repositoryError = RepositoryError.NotFound("Destination wallet not found")

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns fromWallet.right()
        coEvery { walletRepository.updateWalletBalance(fromWallet.id, 500) } returns updatedFromWallet.right()
        coEvery { walletRepository.getWalletByUserId(transfer.toUserId) } returns repositoryError.left()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.WalletNotFound)
            },
            ifRight = { }
        )

        coVerify { walletRepository.getWalletByUserId(transfer.toUserId) }
    }

    @Test
    fun `should return WalletUpdateFailed when destination wallet update fails`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 500)
        val fromWallet = Wallet(id = "wallet123", userId = transfer.fromUserId, balance = 1000)
        val toWallet = Wallet(id = "wallet456", userId = transfer.toUserId, balance = 500)
        val updatedFromWallet = fromWallet.copy(balance = 500)
        val repositoryError = RepositoryError.DatabaseError("Destination update failed")

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns fromWallet.right()
        coEvery { walletRepository.updateWalletBalance(fromWallet.id, 500) } returns updatedFromWallet.right()
        coEvery { walletRepository.getWalletByUserId(transfer.toUserId) } returns toWallet.right()
        coEvery { walletRepository.updateWalletBalance(toWallet.id, 1000) } returns repositoryError.left()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.WalletUpdateFailed)
            },
            ifRight = { }
        )

        coVerify { walletRepository.updateWalletBalance(toWallet.id, 1000) }
        coVerify(exactly = 0) { transactionRepository.create(any()) }
    }

    @Test
    fun `should return TransactionCreationFailed when transaction creation fails`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 500)
        val fromWallet = Wallet(id = "wallet123", userId = transfer.fromUserId, balance = 1000)
        val toWallet = Wallet(id = "wallet456", userId = transfer.toUserId, balance = 500)
        val updatedFromWallet = fromWallet.copy(balance = 500)
        val updatedToWallet = toWallet.copy(balance = 1000)
        val repositoryError = RepositoryError.DatabaseError("Transaction creation failed")

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns fromWallet.right()
        coEvery { walletRepository.updateWalletBalance(fromWallet.id, 500) } returns updatedFromWallet.right()
        coEvery { walletRepository.getWalletByUserId(transfer.toUserId) } returns toWallet.right()
        coEvery { walletRepository.updateWalletBalance(toWallet.id, 1000) } returns updatedToWallet.right()
        coEvery { transactionRepository.create(any()) } returns repositoryError.left()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(error is TransferError.TransactionCreationFailed)
            },
            ifRight = { }
        )

        coVerify { transactionRepository.create(any()) }
    }

    @Test
    fun `should allow transfer of entire balance`() = runTest {
        // Given
        val transfer = Transfer(fromUserId = "user123", toUserId = "user456", amount = 1000)
        val fromWallet = Wallet(id = "wallet123", userId = transfer.fromUserId, balance = 1000)
        val toWallet = Wallet(id = "wallet456", userId = transfer.toUserId, balance = 500)
        val updatedFromWallet = fromWallet.copy(balance = 0)
        val updatedToWallet = toWallet.copy(balance = 1500)

        coEvery { walletRepository.getWalletByUserId(transfer.fromUserId) } returns fromWallet.right()
        coEvery { walletRepository.updateWalletBalance(fromWallet.id, 0) } returns updatedFromWallet.right()
        coEvery { walletRepository.getWalletByUserId(transfer.toUserId) } returns toWallet.right()
        coEvery { walletRepository.updateWalletBalance(toWallet.id, 1500) } returns updatedToWallet.right()
        coEvery { transactionRepository.create(any()) } returns mockk<org.my.firstcircletest.domain.entities.Transaction>().right()

        // When
        val result = useCase.invoke(transfer)

        // Then
        assertTrue(result.isRight())
    }
}
