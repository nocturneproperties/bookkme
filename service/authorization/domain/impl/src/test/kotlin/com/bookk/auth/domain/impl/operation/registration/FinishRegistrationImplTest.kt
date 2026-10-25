package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.UserClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class FinishRegistrationImplTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val userClient = mockk<UserClient>()
        val generateAuthToken = mockk<GenerateAuthToken>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val finishPasskeyRegistration = mockk<FinishPasskeyRegistration>()
        val sut = FinishRegistrationImpl(
            accountDataSource,
            deviceDataSource,
            userClient,
            generateAuthToken,
            eventProducer,
            transactionManager,
            finishPasskeyRegistration
        )
    }

    private fun makeRequest(deviceId: Uuid = Uuid.random()): VerifyAccountCreationRequest = VerifyAccountCreationRequest(
        requestId = "req-id",
        publicKeyCredentialJson = "{}",
        deviceInfo = VerifyAccountCreationRequest.DeviceInfo(deviceUUID = deviceId, deviceName = "Test Device"),
        userInfo = VerifyAccountCreationRequest.UserInfo(name = "John", lastName = "Doe", email = "john@example.com")
    )

    private fun makePasskeyCredential(handle: Uuid = Uuid.random()): PasskeyCredential {
        val now = Clock.System.now()
        return PasskeyCredential(
            id = Uuid.random(),
            authId = Uuid.random(),
            authInfo = Authentication(id = Uuid.random(), userId = Uuid.random(), uuid = Uuid.random()),
            handle = handle,
            name = "Test Passkey",
            credDescriptor = PasskeyCredential.CredentialDescriptor(ByteArray(0), "public-key", emptySet()),
            publicKey = "key",
            signatureCount = 0,
            isDiscoverable = true,
            isBackupEligible = false,
            isBackedUp = false,
            attestationObject = ByteArray(0),
            clientData = "{}",
            createdAt = now,
            lastUsedAt = now
        )
    }

    @Test
    fun `should complete registration successfully and emit DeviceCreated event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val userId = Uuid.random()
        val passkey = makePasskeyCredential()
        val authRecord = Authentication(id = Uuid.random(), userId = userId, uuid = passkey.handle)
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { userClient.createUser(any()) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(any()) } returns authRecord
            coEvery { finishPasskeyRegistration.attachOwner(authRecord.id, passkey) } returns Unit
            coEvery { deviceDataSource.insertDevice(authRecord.id, deviceId, any(), Language.EN) } returns Uuid.random()
            coEvery { generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns Result.success(tokens)
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isSuccess)
        assertEquals(tokens, result.getOrNull())
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.DeviceCreated>(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
    }

    @Test
    fun `should return failure and emit cleanup events when transaction fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val userId = Uuid.random()
        val passkey = makePasskeyCredential()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { userClient.createUser(any()) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(any()) } throws RuntimeException("db failure")
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should return AccountCreationFailed when device insert returns null`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val userId = Uuid.random()
        val passkey = makePasskeyCredential()
        val authRecord = Authentication(id = Uuid.random(), userId = userId, uuid = passkey.handle)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { userClient.createUser(any()) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(any()) } returns authRecord
            coEvery { finishPasskeyRegistration.attachOwner(authRecord.id, passkey) } returns Unit
            coEvery { deviceDataSource.insertDevice(authRecord.id, deviceId, any(), Language.EN) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishRegistration.Error.AccountCreationFailed)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should return failure when passkey verification fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = makeRequest()
        with(fixture) {
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.failure(
                FinishPasskeyRegistration.Error.ChallengeWindowExpired()
            )
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired)
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should return failure when user creation fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = makeRequest()
        val passkey = makePasskeyCredential()
        with(fixture) {
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { userClient.createUser(any()) } returns Result.failure(RuntimeException("user service error"))
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
    }
}
