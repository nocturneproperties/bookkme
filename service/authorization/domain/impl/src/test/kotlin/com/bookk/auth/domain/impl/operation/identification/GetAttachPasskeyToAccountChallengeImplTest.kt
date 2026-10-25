package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceInfo
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetAttachPasskeyToAccountChallengeImplTest {

    private class SutFixture {
        val startPasskeyRegistration = mockk<StartPasskeyRegistration>()
        val accountDataSource = mockk<AccountDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val userClient = mockk<UserClient>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAttachPasskeyToAccountChallengeImpl(
            startPasskeyRegistration,
            accountDataSource,
            deviceDataSource,
            userClient,
            transactionManager
        )
    }

    private fun makeAuthentication(authId: Uuid = Uuid.random()): Authentication =
        Authentication(id = authId, userId = Uuid.random(), uuid = Uuid.random())

    private fun makeDevice(deviceId: Uuid = Uuid.random()): Device = Device(
        authRecord = makeAuthentication(),
        deviceInfo = DeviceInfo(
            id = deviceId,
            deviceUUID = Uuid.random(),
            refreshToken = null,
            previousRefreshToken = null,
            deviceName = "iPhone 15",
            isSignedIn = true
        )
    )

    private fun makeUser(userId: Uuid = Uuid.random()): UserSnapshot = UserSnapshot.stub(
        id = userId,
        name = "John",
        lastName = "Doe",
        email = "john@example.com"
    )

    private fun makeChallengeResponse(): RegistrationChallengeResponse = RegistrationChallengeResponse(
        requestId = "req-id",
        challenge = "challenge",
        challengeJson = "{}",
        userHandle = "handle",
        displayName = "John Doe - iPhone 15"
    )

    @Test
    fun `should return challenge response when all dependencies resolve successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val userId = Uuid.random()
        val auth = makeAuthentication(authId)
        val device = makeDevice(deviceId)
        val user = makeUser(userId)
        val challenge = makeChallengeResponse()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authId) } returns auth
            coEvery { deviceDataSource.getDeviceById(deviceId) } returns device
            coEvery { userClient.getUserById(userId) } returns Result.success(user)
            coEvery { startPasskeyRegistration(auth.uuid, any()) } returns Result.success(challenge)
        }

        whenn()
        val result = fixture.sut.invoke(authId, deviceId, userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(challenge, result.getOrNull())
    }

    @Test
    fun `should return UnableToGeneratePasskeyChallenge when auth record not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(authId, deviceId, userId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAttachPasskeyToAccountChallenge.Error.UnableToGeneratePasskeyChallenge)
    }

    @Test
    fun `should return UnableToGeneratePasskeyChallenge when device not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val userId = Uuid.random()
        val auth = makeAuthentication(authId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authId) } returns auth
            coEvery { deviceDataSource.getDeviceById(deviceId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(authId, deviceId, userId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAttachPasskeyToAccountChallenge.Error.UnableToGeneratePasskeyChallenge)
    }

    @Test
    fun `should return UnableToGeneratePasskeyChallenge when user client fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val userId = Uuid.random()
        val auth = makeAuthentication(authId)
        val device = makeDevice(deviceId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.getAuthRecordById(authId) } returns auth
            coEvery { deviceDataSource.getDeviceById(deviceId) } returns device
            coEvery { userClient.getUserById(userId) } returns Result.failure(RuntimeException("user service unavailable"))
        }

        whenn()
        val result = fixture.sut.invoke(authId, deviceId, userId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAttachPasskeyToAccountChallenge.Error.UnableToGeneratePasskeyChallenge)
    }
}
