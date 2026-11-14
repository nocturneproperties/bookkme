package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.ErasedAccountDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class DeleteAccountImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val authRecord = Authentication.stub(userId = userId)
        val confirmationTokens = ActionConfirmationTestTokens()
        val accountDataSource = mockk<AccountDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val erasedAccountDataSource = mockk<ErasedAccountDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val tokenSpentWhenReading = mutableListOf<Boolean>()
        val recordedInTransaction = mutableListOf<Boolean>()
        val sut =
            DeleteAccountImpl(
                confirmationTokens.gate,
                accountDataSource,
                AccountEraser(accountDataSource, deviceDataSource, erasedAccountDataSource, eventProducer),
                transactionManager,
            )

        init {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordByUserId(userId) } coAnswers {
                tokenSpentWhenReading += confirmationTokens.isSpent(TOKEN_ID)
                authRecord
            }
            coEvery { deviceDataSource.getDevices(authRecord.id) } returns emptyList()
            coEvery { erasedAccountDataSource.record(userId) } coAnswers {
                recordedInTransaction += transactionManager.isInTransaction()
            }
            coEvery { accountDataSource.deleteAuthorization(authRecord.id) } returns Unit
        }

        fun token(
            grant: ActionConfirmationGrant = ActionConfirmationGrant.accountDeletion(
                userId,
            ),
        ) = confirmationTokens.tokenFor(grant, tokenId = TOKEN_ID)
    }

    private fun makeDevice(authRecord: Authentication, deviceUUID: Uuid = Uuid.random()): Device = Device(
        authRecord = authRecord,
        deviceInfo = DeviceInfo(
            id = Uuid.random(),
            deviceUUID = deviceUUID,
            refreshToken = null,
            previousRefreshToken = null,
            deviceName = "Test Device",
            isSignedIn = true,
        ),
    )

    @Test
    fun `should delete the account record by its own id and emit user and device deleted events`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUUID = Uuid.random()
        coEvery { fixture.deviceDataSource.getDevices(fixture.authRecord.id) } returns
            listOf(makeDevice(fixture.authRecord, deviceUUID))

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.accountDataSource.deleteAuthorization(fixture.authRecord.id) }
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == fixture.userId }, any())
        }
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<AuthEvent.DeviceDeleted> {
                    it.authId ==
                        fixture.authRecord.id &&
                        it.deviceUuid == deviceUUID
                },
                any(),
            )
        }
        assertEquals(listOf(true), fixture.recordedInTransaction)
        assertEquals(listOf(true), fixture.tokenSpentWhenReading)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should emit DeviceDeleted event for each device when account has multiple devices`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val devices = List(3) { makeDevice(fixture.authRecord) }
        coEvery { fixture.deviceDataSource.getDevices(fixture.authRecord.id) } returns devices

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        devices.forEach { device ->
            coVerify(exactly = 1) {
                fixture.eventProducer.send(
                    match<AuthEvent.DeviceDeleted> {
                        it.authId ==
                            fixture.authRecord.id &&
                            it.deviceUuid == device.deviceInfo.deviceUUID
                    },
                    any(),
                )
            }
        }
    }

    @Test
    fun `should refuse mismatched action confirmation claims before opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val grant = ActionConfirmationGrant.accountDeletion(fixture.userId)
        val wrongGrants = listOf(
            grant.copy(userId = Uuid.random()),
            grant.copy(targetId = Uuid.random()),
            grant.copy(businessId = Uuid.random()),
            ActionConfirmationGrant.personalDataExport(fixture.userId),
        )

        whenn()
        val results = wrongGrants.map { fixture.sut(fixture.userId, fixture.token(it)) }

        then()
        results.forEach { assertTrue(it.exceptionOrNull() is DeleteAccount.Error.ActionConfirmationInvalid) }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Unit>(any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.getAuthRecordByUserId(any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(), any()) }
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should refuse missing malformed and expired action confirmation tokens`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expired = fixture.confirmationTokens.tokenFor(
            ActionConfirmationGrant.accountDeletion(fixture.userId),
            expiresIn = (-1).seconds,
        )

        whenn()
        val results = listOf("", "not-a-token", expired).map { fixture.sut(fixture.userId, it) }

        then()
        results.forEach { assertTrue(it.exceptionOrNull() is DeleteAccount.Error.ActionConfirmationInvalid) }
        coVerify(exactly = 0) { fixture.accountDataSource.getAuthRecordByUserId(any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.deleteAuthorization(any()) }
    }

    @Test
    fun `should refuse an already used action confirmation token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token()
        fixture.sut(fixture.userId, token).getOrThrow()

        whenn()
        val result = fixture.sut(fixture.userId, token)

        then()
        assertTrue(result.exceptionOrNull() is DeleteAccount.Error.ActionConfirmationInvalid)
        coVerify(exactly = 1) { fixture.accountDataSource.deleteAuthorization(fixture.authRecord.id) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should return InvalidCredentials and release the token when the account is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(fixture.userId) } returns null

        whenn()
        val result = fixture.sut(fixture.userId, fixture.token())

        then()
        assertTrue(result.exceptionOrNull() is DeleteAccount.Error.InvalidCredentials)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
        coVerify(exactly = 0) { fixture.erasedAccountDataSource.record(any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(), any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.deleteAuthorization(any()) }
    }

    @Test
    fun `should allow retrying with the same token after a transaction fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = fixture.token()
        val failure = IllegalStateException("deletion failed")
        coEvery { fixture.accountDataSource.deleteAuthorization(fixture.authRecord.id) } throws failure

        whenn()
        val failed = fixture.sut(fixture.userId, token)
        val spentAfterFailure = fixture.confirmationTokens.isSpent(TOKEN_ID)
        coEvery { fixture.accountDataSource.deleteAuthorization(fixture.authRecord.id) } returns Unit
        val retried = fixture.sut(fixture.userId, token)

        then()
        assertEquals(failure, failed.exceptionOrNull())
        assertFalse(spentAfterFailure)
        assertTrue(retried.isSuccess)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should release the token and propagate cancellation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(fixture.userId) } throws
            CancellationException("request cancelled")

        whenn()
        val result = runCatching { fixture.sut(fixture.userId, fixture.token()) }

        then()
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
        coVerify(exactly = 0) { fixture.accountDataSource.deleteAuthorization(any()) }
    }

    private companion object {
        const val TOKEN_ID = "token-id"
    }
}
