package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.VerifySignInRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SignInImplTest {

    private class SutFixture {
        val finishAssertion = mockk<FinishAssertion>()
        val generateAuthToken = mockk<GenerateAuthToken>()
        val deviceDataSource = mockk<DeviceDataSource>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val producer = mockk<StandardEventProducer>(relaxed = true)
        val sut = SignInImpl(finishAssertion, generateAuthToken, deviceDataSource, transactionManager, producer)
    }

    private fun makeRequest(deviceId: Uuid = Uuid.random()): VerifySignInRequest = VerifySignInRequest(
        requestId = "req-id",
        publicKeyCredentialJson = "{}",
        deviceInfo = VerifySignInRequest.DeviceInfo(
            deviceUUID = deviceId,
            deviceName = "Test Device"
        )
    )

    private fun makePasskeyCredential(authId: Uuid = Uuid.random()): PasskeyCredential {
        return PasskeyCredential(
            id = Uuid.random(),
            authId = authId,
            authInfo = Authentication(id = authId, userId = Uuid.random(), uuid = Uuid.random()),
            handle = Uuid.random(),
            name = "test-passkey",
            credDescriptor = PasskeyCredential.CredentialDescriptor(ByteArray(0), "public-key", emptySet()),
            publicKey = "key",
            signatureCount = 0,
            isDiscoverable = true,
            isBackupEligible = false,
            isBackedUp = false,
            attestationObject = ByteArray(0),
            clientData = "{}",
            createdAt = Clock.System.now(),
            lastUsedAt = Clock.System.now()
        )
    }

    @Test
    fun `should sign in successfully and return auth tokens`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val credential = makePasskeyCredential(authId)
        val tokens = AuthTokens(accessToken = "access-token", refreshToken = "refresh-token")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { deviceDataSource.insertDevice(authId, deviceId, any(), Language.EN) } returns null
            coEvery { generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns Result.success(tokens)
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isSuccess)
        assertEquals(tokens, result.getOrNull())
        coVerify(exactly = 1) { fixture.deviceDataSource.insertDevice(authId, deviceId, any(), Language.EN) }
        coVerify(exactly = 0) { fixture.producer.send(any<AuthEvent.DeviceCreated>(), any()) }
    }

    @Test
    fun `should emit DeviceCreated event when device is newly registered`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val credential = makePasskeyCredential(authId)
        val tokens = AuthTokens(accessToken = "access-token", refreshToken = "refresh-token")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { deviceDataSource.insertDevice(authId, deviceId, any(), Language.EN) } returns Uuid.random()
            coEvery { generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns Result.success(tokens)
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isSuccess)
        assertEquals(tokens, result.getOrNull())
        coVerify(exactly = 1) { fixture.producer.send(any<AuthEvent.DeviceCreated>(), any()) }
        coVerify(exactly = 0) { fixture.deviceDataSource.updateLanguage(any(), any(), any()) }
    }

    @Test
    fun `should refresh language and emit DeviceLanguageUpdated when device already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val credential = makePasskeyCredential(authId)
        val tokens = AuthTokens(accessToken = "access-token", refreshToken = "refresh-token")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { deviceDataSource.insertDevice(authId, deviceId, any(), Language.UK) } returns null
            coEvery { generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns Result.success(tokens)
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.UK)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.updateLanguage(authId, deviceId, Language.UK) }
        coVerify(exactly = 1) { fixture.producer.send(any<AuthEvent.DeviceLanguageUpdated>(), any()) }
        coVerify(exactly = 0) { fixture.producer.send(any<AuthEvent.DeviceCreated>(), any()) }
    }

    @Test
    fun `should return failure when finish assertion fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = makeRequest()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.failure(FinishAssertion.Error.PasskeyOwnerNotFound())
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.PasskeyOwnerNotFound)
    }

    @Test
    fun `should return failure when passkey verification fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = makeRequest()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.failure(FinishAssertion.Error.VerificationFailed())
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.VerificationFailed)
    }

    @Test
    fun `should return failure when challenge window is expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = makeRequest()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.failure(FinishAssertion.Error.ChallengeWindowExpired())
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FinishAssertion.Error.ChallengeWindowExpired)
    }

    @Test
    fun `should return failure when token generation fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceId = Uuid.random()
        val request = makeRequest(deviceId)
        val credential = makePasskeyCredential(authId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishAssertion(request) } returns Result.success(credential)
            coEvery { deviceDataSource.insertDevice(authId, deviceId, any(), Language.EN) } returns null
            coEvery { generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns Result.failure(
                GenerateAuthToken.Error.InvalidCredentials()
            )
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GenerateAuthToken.Error.InvalidCredentials)
    }
}
