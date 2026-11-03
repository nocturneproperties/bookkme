package com.bookk.auth.domain.impl.operation.export

import com.bookk.auth.domain.api.authentication.entity.AccountActivity
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.export.AccountDataExport
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ExportAccountDataImplTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>()
        val passKeyDataSource = mockk<PassKeyDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val readInTransaction = mutableListOf<Boolean>()
        val account = Authentication.stub()
        val sut = ExportAccountDataImpl(accountDataSource, passKeyDataSource, deviceDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAccountActivity(account.userId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                AccountActivity(account.id, createdAt = Instant.fromEpochMilliseconds(1_000), lastActiveAt = Instant.fromEpochMilliseconds(2_000))
            }
            coEvery { passKeyDataSource.getCredentialBy(account.id) } returns emptyList()
            coEvery { deviceDataSource.getDevices(account.id) } returns emptyList()
        }
    }

    @Test
    fun `should export the account's dates, passkeys and devices`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.passKeyDataSource.getCredentialBy(fixture.account.id) } returns listOf(
            PasskeyCredential.stub(
                authInfo = fixture.account,
                name = "iPhone",
                isBackedUp = true,
                createdAt = Instant.fromEpochMilliseconds(3_000),
                lastUsedAt = Instant.fromEpochMilliseconds(4_000),
            ),
        )
        coEvery { fixture.deviceDataSource.getDevices(fixture.account.id) } returns listOf(
            Device.stub(fixture.account, DeviceInfo.stub(deviceName = "Pixel 9", isSignedIn = false, lastLogInAt = Instant.fromEpochMilliseconds(5_000))),
        )

        whenn()
        val result = fixture.sut(fixture.account.userId)

        then()
        assertEquals(
            AccountDataExport(
                createdAt = Instant.fromEpochMilliseconds(1_000),
                lastActiveAt = Instant.fromEpochMilliseconds(2_000),
                passkeys = listOf(AccountDataExport.PasskeyExport("iPhone", Instant.fromEpochMilliseconds(3_000), Instant.fromEpochMilliseconds(4_000), backedUp = true)),
                devices = listOf(AccountDataExport.DeviceExport("Pixel 9", signedIn = false, lastSignInAt = Instant.fromEpochMilliseconds(5_000))),
            ),
            result.getOrNull(),
        )
    }

    @Test
    fun `should read the account inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.account.userId)

        then()
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail for a user without an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val unknownUserId = Uuid.random()
        coEvery { fixture.accountDataSource.getAccountActivity(unknownUserId) } returns null

        whenn()
        val result = fixture.sut(unknownUserId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }
}
