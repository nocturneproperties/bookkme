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
import com.bookk.auth.domain.impl.email.AuthEmails
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.error.UserErrorCodes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import library.email.EmailCodeError
import library.email.test.EmailTestMailbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        val mailbox = EmailTestMailbox()
        val sut = FinishRegistrationImpl(
            accountDataSource,
            deviceDataSource,
            userClient,
            generateAuthToken,
            eventProducer,
            transactionManager,
            finishPasskeyRegistration,
            mailbox.redeemer,
        )

        suspend fun signUpRequest(
            requestId: String = Uuid.random().toString(),
            verifiedEmail: String = VERIFIED_EMAIL,
            deviceId: Uuid = Uuid.random(),
            acceptedDocuments: AcceptedLegalDocuments =
                AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01"),
            purpose: String = EmailPurpose.SIGN_UP,
        ): VerifyAccountCreationRequest {
            val started = mailbox.issuer.issue(verifiedEmail, purpose, Uuid.parse(requestId)) {
                AuthEmails.signUpCode(Language.EN, it)
            }.getOrThrow()
            return VerifyAccountCreationRequest(
                requestId = requestId,
                publicKeyCredentialJson = "{}",
                deviceInfo = VerifyAccountCreationRequest.DeviceInfo(deviceUUID = deviceId, deviceName = "Test Device"),
                userInfo = VerifyAccountCreationRequest.UserInfo(
                    name = "John",
                    lastName = "Doe",
                    email = "typed@example.com",
                ),
                acceptedDocuments = acceptedDocuments,
                verificationId = started.verificationId,
                emailCode = mailbox.codeSentTo(verifiedEmail),
            )
        }

        fun registrationSucceeds(
            request: VerifyAccountCreationRequest,
            userId: Uuid = Uuid.random(),
            tokens: AuthTokens = AuthTokens("access", "refresh"),
        ) {
            val passkey = PasskeyCredential.stub()
            val authRecord = Authentication.stub(userId = userId, uuid = passkey.handle)
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns Result.success(passkey)
            coEvery { userClient.createUser(any()) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(any()) } returns authRecord
            coEvery { finishPasskeyRegistration.attachOwner(authRecord.id, passkey) } returns Unit
            coEvery {
                deviceDataSource.insertDevice(authRecord.id, request.deviceInfo.deviceUUID, any(), Language.EN)
            } returns
                Uuid.random()
            coEvery { generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns
                Result.success(tokens)
        }
    }

    @Test
    fun `should complete registration successfully and emit DeviceCreated event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        fixture.registrationSucceeds(request, tokens = tokens)

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertEquals(tokens, result.getOrThrow())
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.DeviceCreated>(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
    }

    @Test
    fun `should create the user with the verified email and the legal documents accepted at sign-up`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val accepted = AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01")
        val request = fixture.signUpRequest(acceptedDocuments = accepted)
        val sent = slot<CreateUserRequest>()
        fixture.registrationSucceeds(request)
        coEvery { fixture.userClient.createUser(capture(sent)) } returns Result.success(Uuid.random())

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(accepted, sent.captured.acceptedDocuments)
        assertEquals(listOf("John", "Doe", VERIFIED_EMAIL), with(sent.captured) { listOf(name, lastName, email) })
    }

    @Test
    fun `should return Invalid and keep the passkey challenge when the code is wrong`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        val wrongCode = request.copy(emailCode = if (request.emailCode == "000000") "000001" else "000000")

        whenn()
        val result = fixture.sut.invoke(wrongCode, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Invalid) { result.toString() }
        coVerify(exactly = 0) { fixture.finishPasskeyRegistration.verifyRequest(any()) }
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should accept the right code after a wrong one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        fixture.registrationSucceeds(request)
        fixture.sut.invoke(
            request.copy(
                emailCode = if (request.emailCode ==
                    "000000"
                ) {
                    "000001"
                } else {
                    "000000"
                },
            ),
            Language.EN,
        )

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }

    @Test
    fun `should return Expired when the code was sent for another sign-up attempt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        val otherAttempt = request.copy(requestId = Uuid.random().toString())

        whenn()
        val result = fixture.sut.invoke(otherAttempt, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should return Invalid when the code was issued for something other than sign-up`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest(purpose = EmailPurpose.EMAIL_CHANGE)

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Invalid) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should return ChallengeWindowExpired when the request id names no challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest().copy(requestId = "not-a-challenge")

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired) {
            result.toString()
        }
    }

    @Test
    fun `should refuse the code once an account was created with it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        fixture.registrationSucceeds(request)
        fixture.sut.invoke(request, Language.EN).getOrThrow()

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        coVerify(exactly = 1) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should give the code back when registration fails after it was checked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        coEvery { fixture.finishPasskeyRegistration.verifyRequest(request) } returns
            Result.success(PasskeyCredential.stub())
        coEvery { fixture.userClient.createUser(any()) } returns Result.failure(RuntimeException("user service error"))
        fixture.sut.invoke(request, Language.EN)

        whenn()
        val redeemedAgain = fixture.mailbox.redeemer.redeem(
            request.verificationId,
            request.emailCode,
            Uuid.parse(request.requestId),
        ) {
            it.email
        }

        then()
        assertEquals(VERIFIED_EMAIL, redeemedAgain.getOrThrow())
    }

    @Test
    fun `should fail with the user service's error and create no account for an unpublished version`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest(
            acceptedDocuments = AcceptedLegalDocuments(privacyPolicy = "unpublished", termsOfService = null),
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { finishPasskeyRegistration.verifyRequest(request) } returns
                Result.success(PasskeyCredential.stub())
            coEvery { userClient.createUser(any()) } returns Result.failure(LegalDocumentError.VersionUnknown())
        }

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertEquals(UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN, (result.exceptionOrNull() as? BusinessError)?.code)
        coVerify(exactly = 0) { fixture.accountDataSource.createAuthorization(any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
    }

    @Test
    fun `should compensate the remote user but keep device associations when registration fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        fixture.registrationSucceeds(request)
        coEvery { fixture.accountDataSource.createAuthorization(any()) } throws RuntimeException("db failure")

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should emit user cleanup inside a transaction when registration fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        val eventsSentOutsideTransaction = mutableListOf<Any>()
        fixture.registrationSucceeds(request)
        with(fixture) {
            coEvery { accountDataSource.createAuthorization(any()) } throws RuntimeException("db failure")
            coEvery { eventProducer.send(any<AuthEvent>(), any()) } coAnswers {
                if (!transactionManager.isInTransaction()) eventsSentOutsideTransaction += firstArg<AuthEvent>()
            }
        }

        whenn()
        fixture.sut.invoke(request, Language.EN)

        then()
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
        assertEquals(emptyList<Any>(), eventsSentOutsideTransaction)
    }

    @Test
    fun `should return AccountCreationFailed when device insert returns null`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        fixture.registrationSucceeds(request)
        coEvery {
            fixture.deviceDataSource.insertDevice(any(), request.deviceInfo.deviceUUID, any(), Language.EN)
        } returns
            null

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is FinishRegistration.Error.AccountCreationFailed)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.UserDeleted>(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should only compensate the remote user when issuing tokens fails after a staged device event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        val userId = Uuid.random()
        fixture.registrationSucceeds(request, userId = userId)
        coEvery { fixture.generateAuthToken(any<GenerateAuthToken.Source.InitialAuthentication>()) } returns
            Result.failure(RuntimeException("token failure"))

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { fixture.eventProducer.send(any<AuthEvent.DeviceCreated>(), any()) }
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == userId }, any())
        }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should return failure when passkey verification fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        coEvery { fixture.finishPasskeyRegistration.verifyRequest(request) } returns Result.failure(
            FinishPasskeyRegistration.Error.ChallengeWindowExpired(),
        )

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired)
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should return failure when user creation fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.signUpRequest()
        coEvery { fixture.finishPasskeyRegistration.verifyRequest(request) } returns
            Result.success(PasskeyCredential.stub())
        coEvery { fixture.userClient.createUser(any()) } returns Result.failure(RuntimeException("user service error"))

        whenn()
        val result = fixture.sut.invoke(request, Language.EN)

        then()
        assertTrue(result.isFailure)
    }

    private companion object {
        const val VERIFIED_EMAIL = "john@example.com"
    }
}
