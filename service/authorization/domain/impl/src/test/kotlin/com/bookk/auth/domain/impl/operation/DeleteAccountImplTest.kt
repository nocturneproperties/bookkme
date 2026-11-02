package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteAccountImplTest {

    private class SutFixture {
        val finishAssertion = mockk<FinishAssertion>()
        val accountDataSource = mockk<AccountDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteAccountImpl(finishAssertion, accountDataSource, AccountEraser(accountDataSource, deviceDataSource, eventProducer), transactionManager)
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

    private fun makeRequest(): FinishAssertionRequest = object : FinishAssertionRequest {
        override val requestId = "req-id"
        override val publicKeyCredentialJson = "{}"
    }

    @Test
    fun `should delete the account record by its own id and emit user and device deleted events`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val authRecord = Authentication(id = Uuid.random(), userId = userId, uuid = Uuid.random())
        val deviceUUID = Uuid.random()
        val device = makeDevice(authRecord, deviceUUID)
        val request = makeRequest()
        val credential = PasskeyCredential.stub(authInfo = Authentication.stub(userId = userId))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { accountDataSource.getAuthRecordByUserId(userId) } returns authRecord
            coEvery { deviceDataSource.getDevices(authRecord.id) } returns listOf(device)
            coEvery { accountDataSource.deleteAuthorization(authRecord.id) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(userId, request)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.accountDataSource.deleteAuthorization(authRecord.id) }
        coVerify(atLeast = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(atLeast = 1) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should emit DeviceDeleted event for each device when account has multiple devices`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val authRecord = Authentication(id = Uuid.random(), userId = userId, uuid = Uuid.random())
        val device1 = makeDevice(authRecord)
        val device2 = makeDevice(authRecord)
        val device3 = makeDevice(authRecord)
        val request = makeRequest()
        val credential = PasskeyCredential.stub(authInfo = Authentication.stub(userId = userId))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { accountDataSource.getAuthRecordByUserId(userId) } returns authRecord
            coEvery { deviceDataSource.getDevices(authRecord.id) } returns listOf(device1, device2, device3)
            coEvery { accountDataSource.deleteAuthorization(authRecord.id) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(userId, request)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(exactly = 3) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should return failure when finish assertion fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val request = makeRequest()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.failure(FinishAssertion.Error.VerificationFailed())
        }

        whenn()
        val result = fixture.sut.invoke(userId, request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.VerificationFailed)
    }

    @Test
    fun `should throw InvalidCredentials when auth record not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val request = makeRequest()
        val credential = PasskeyCredential.stub(authInfo = Authentication.stub(userId = userId))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { accountDataSource.getAuthRecordByUserId(userId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeleteAccount.Error.InvalidCredentials)
    }

    @Test
    fun `should not delete the account when the passkey belongs to another account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val request = makeRequest()
        val otherAccountsPasskey = PasskeyCredential.stub(authInfo = Authentication.stub(userId = Uuid.random()))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(otherAccountsPasskey)
        }

        whenn()
        val result = fixture.sut(userId, request)

        then()
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyNotOwned)
        coVerify(exactly = 0) { fixture.eventProducer.send(any(), any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.deleteAuthorization(any()) }
    }
}
