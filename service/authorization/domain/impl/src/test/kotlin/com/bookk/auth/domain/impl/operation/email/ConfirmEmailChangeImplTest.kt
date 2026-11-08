package com.bookk.auth.domain.impl.operation.email

import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.impl.email.AuthEmails
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.UserClient
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.email.EmailCodeError
import library.email.EmailVerificationStarted
import library.email.test.EmailTestMailbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ConfirmEmailChangeImplTest {

    private class SutFixture {
        val userClient = mockk<UserClient>()
        val mailbox = EmailTestMailbox()
        val sut = ConfirmEmailChangeImpl(userClient, mailbox.redeemer)

        suspend fun codeFor(userId: Uuid, purpose: String = EmailPurpose.EMAIL_CHANGE): Pair<EmailVerificationStarted, String> {
            val started = mailbox.issuer.issue(NEW_EMAIL, purpose, userId) { AuthEmails.emailChangeCode(Language.EN, it) }.getOrThrow()
            return started to mailbox.codeSentTo(NEW_EMAIL)
        }
    }

    @Test
    fun `should store the verified address for the caller`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val (started, code) = fixture.codeFor(userId)
        coEvery { fixture.userClient.changeEmail(userId, NEW_EMAIL) } returns Result.success(Unit)

        whenn()
        val result = fixture.sut.invoke(userId, started.verificationId, code)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.userClient.changeEmail(userId, NEW_EMAIL) }
    }

    @Test
    fun `should refuse the code once the address was stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val (started, code) = fixture.codeFor(userId)
        coEvery { fixture.userClient.changeEmail(userId, NEW_EMAIL) } returns Result.success(Unit)
        fixture.sut.invoke(userId, started.verificationId, code).getOrThrow()

        whenn()
        val result = fixture.sut.invoke(userId, started.verificationId, code)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        coVerify(exactly = 1) { fixture.userClient.changeEmail(any(), any()) }
    }

    @Test
    fun `should return Invalid and store nothing when the code is wrong`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val (started, code) = fixture.codeFor(userId)

        whenn()
        val result = fixture.sut.invoke(userId, started.verificationId, if (code == "000000") "000001" else "000000")

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Invalid) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.changeEmail(any(), any()) }
    }

    @Test
    fun `should return Expired when the code belongs to another account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val (started, code) = fixture.codeFor(Uuid.random())

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), started.verificationId, code)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.changeEmail(any(), any()) }
    }

    @Test
    fun `should return Invalid when the code was issued for something other than an email change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val (started, code) = fixture.codeFor(userId, purpose = EmailPurpose.SIGN_UP)

        whenn()
        val result = fixture.sut.invoke(userId, started.verificationId, code)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Invalid) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.changeEmail(any(), any()) }
    }

    @Test
    fun `should return EmailAlreadyExist and give the code back when another account took the address meanwhile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val (started, code) = fixture.codeFor(userId)
        coEvery { fixture.userClient.changeEmail(userId, NEW_EMAIL) } returns Result.failure(
            BusinessError(HttpStatusCode.UnprocessableEntity.value, UserErrorCodes.USER_CONTACT_TAKEN, "taken"),
        )

        whenn()
        val result = fixture.sut.invoke(userId, started.verificationId, code)

        then()
        assertTrue(result.exceptionOrNull() is ConfirmEmailChange.Error.EmailAlreadyExist) { result.toString() }
        val redeemedAgain = fixture.mailbox.redeemer.redeem(started.verificationId, code, userId) { it.email }
        assertEquals(NEW_EMAIL, redeemedAgain.getOrThrow())
    }

    @Test
    fun `should propagate any other user service error`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val (started, code) = fixture.codeFor(userId)
        val notFound = BusinessError(HttpStatusCode.NotFound.value, UserErrorCodes.USER_NOT_EXIST, "User not found")
        coEvery { fixture.userClient.changeEmail(userId, NEW_EMAIL) } returns Result.failure(notFound)

        whenn()
        val result = fixture.sut.invoke(userId, started.verificationId, code)

        then()
        assertEquals(notFound, result.exceptionOrNull())
    }

    private companion object {
        const val NEW_EMAIL = "new@example.com"
    }
}
