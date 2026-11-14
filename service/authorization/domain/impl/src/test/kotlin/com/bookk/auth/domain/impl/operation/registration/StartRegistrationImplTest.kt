package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.email.EmailCodeError
import library.email.EmailSendError
import library.email.test.EmailTestMailbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class StartRegistrationImplTest {

    private class SutFixture {
        val userClient = mockk<UserClient>()
        val startPasskeyRegistration = mockk<StartPasskeyRegistration>()
        val mailbox = EmailTestMailbox()
        val sut = StartRegistrationImpl(userClient, startPasskeyRegistration, mailbox.issuer)

        fun emailIsFree(email: String) {
            coEvery { userClient.getUserByEmail(email) } returns Result.failure(
                object : BusinessError(HttpStatusCode.NotFound.value, 0, "not found") {},
            )
        }
    }

    private val validRequest = CreateAccountRequest(
        firstName = "John",
        lastName = "Doe",
        email = "john.doe@example.com",
    )

    @Test
    fun `should return the passkey challenge together with the email verification`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val challenge = RegistrationChallengeResponse.stub()
        with(fixture) {
            emailIsFree(validRequest.email)
            coEvery { startPasskeyRegistration(any(), "John Doe") } returns Result.success(challenge)
        }

        whenn()
        val result = fixture.sut.invoke(validRequest, Language.EN)

        then()
        val response = result.getOrThrow()
        assertEquals(challenge.requestId, response.requestId)
        assertEquals(challenge.challengeJson, response.challengeJson)
        assertEquals(challenge.userHandle, response.userHandle)
    }

    @Test
    fun `should email a code bound to the passkey challenge`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val challenge = RegistrationChallengeResponse.stub()
        with(fixture) {
            emailIsFree(validRequest.email)
            coEvery { startPasskeyRegistration(any(), any()) } returns Result.success(challenge)
        }

        whenn()
        val response = fixture.sut.invoke(validRequest, Language.EN).getOrThrow()

        then()
        val code = fixture.mailbox.codeSentTo(validRequest.email)
        val redeemed = fixture.mailbox.redeemer.redeem(
            response.emailVerification.verificationId,
            code,
            Uuid.parse(challenge.requestId),
        ) {
            it.email
        }
        assertEquals(validRequest.email, redeemed.getOrThrow())
    }

    @Test
    fun `should write the code email in the caller's language`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            emailIsFree(validRequest.email)
            coEvery { startPasskeyRegistration(any(), any()) } returns
                Result.success(RegistrationChallengeResponse.stub())
        }

        whenn()
        fixture.sut.invoke(validRequest, Language.PL)

        then()
        val email = fixture.mailbox.sent.single()
        assertTrue(email.content.subject.contains("rejestracji")) { email.toString() }
    }

    @Test
    fun `should return InvalidEmailFormat and send nothing when email format is invalid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val invalidRequest = validRequest.copy(email = "not-an-email")

        whenn()
        val result = fixture.sut.invoke(invalidRequest, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartRegistration.Error.InvalidEmailFormat)
        assertTrue(fixture.mailbox.sent.isEmpty())
    }

    @Test
    fun `should return EmailAlreadyExist and send nothing when user with email already exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userClient.getUserByEmail(validRequest.email) } returns
            Result.success(UserSnapshot.stub(email = validRequest.email))

        whenn()
        val result = fixture.sut.invoke(validRequest, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartRegistration.Error.EmailAlreadyExist)
        assertTrue(fixture.mailbox.sent.isEmpty())
    }

    @Test
    fun `should propagate error when user client returns non-404 business error`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val serverError = object : BusinessError(HttpStatusCode.InternalServerError.value, 0, "server error") {}
        coEvery { fixture.userClient.getUserByEmail(validRequest.email) } returns Result.failure(serverError)

        whenn()
        val result = fixture.sut.invoke(validRequest, Language.EN)

        then()
        assertEquals(serverError, result.exceptionOrNull())
    }

    @Test
    fun `should return ResendTooSoon when a code was sent to the address within the cooldown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            emailIsFree(validRequest.email)
            coEvery { startPasskeyRegistration(any(), any()) } answers
                { Result.success(RegistrationChallengeResponse.stub()) }
        }
        fixture.sut.invoke(validRequest, Language.EN).getOrThrow()

        whenn()
        val result = fixture.sut.invoke(validRequest, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.ResendTooSoon) { result.toString() }
    }

    @Test
    fun `should return Undeliverable when the address can't receive email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            emailIsFree(validRequest.email)
            coEvery { startPasskeyRegistration(any(), any()) } returns
                Result.success(RegistrationChallengeResponse.stub())
            mailbox.refusesDelivery = true
        }

        whenn()
        val result = fixture.sut.invoke(validRequest, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
    }

    @Test
    fun `should send no code when the passkey challenge can't be created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            emailIsFree(validRequest.email)
            coEvery { startPasskeyRegistration(any(), any()) } returns
                Result.failure(IllegalStateException("no challenge"))
        }

        whenn()
        val result = fixture.sut.invoke(validRequest, Language.EN)

        then()
        assertTrue(result.isFailure)
        assertTrue(fixture.mailbox.sent.isEmpty())
        coVerify(exactly = 1) { fixture.startPasskeyRegistration(any(), any()) }
    }
}
