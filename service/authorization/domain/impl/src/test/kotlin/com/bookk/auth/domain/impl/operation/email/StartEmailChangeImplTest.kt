package com.bookk.auth.domain.impl.operation.email

import com.bookk.auth.domain.api.email.operation.StartEmailChange
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
import io.mockk.mockk
import library.email.EmailCodeError
import library.email.EmailSendError
import library.email.test.EmailTestMailbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class StartEmailChangeImplTest {

    private class SutFixture {
        val userClient = mockk<UserClient>()
        val mailbox = EmailTestMailbox()
        val sut = StartEmailChangeImpl(userClient, mailbox.issuer)

        fun emailIsFree(email: String) {
            coEvery { userClient.getUserByEmail(email) } returns Result.failure(
                BusinessError(HttpStatusCode.NotFound.value, 0, "not found"),
            )
        }
    }

    @Test
    fun `should email a code for the new address that only the caller can redeem`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.emailIsFree(NEW_EMAIL)

        whenn()
        val started = fixture.sut.invoke(userId, NEW_EMAIL, Language.EN).getOrThrow()

        then()
        val code = fixture.mailbox.codeSentTo(NEW_EMAIL)
        val redeemed = fixture.mailbox.redeemer.redeem(started.verificationId, code, userId) { it.email }
        assertEquals(NEW_EMAIL, redeemed.getOrThrow())
    }

    @Test
    fun `should write the code email in the caller's language`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.emailIsFree(NEW_EMAIL)

        whenn()
        fixture.sut.invoke(Uuid.random(), NEW_EMAIL, Language.UK)

        then()
        val email = fixture.mailbox.sent.single()
        assertTrue(email.content.subject.contains("адресу")) { email.toString() }
    }

    @Test
    fun `should return InvalidEmailFormat and send nothing for a malformed address`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), "not-an-email", Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartEmailChange.Error.InvalidEmailFormat) { result.toString() }
        assertTrue(fixture.mailbox.sent.isEmpty())
    }

    @Test
    fun `should return EmailAlreadyExist and send nothing when an account holds the address`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userClient.getUserByEmail(NEW_EMAIL) } returns
            Result.success(UserSnapshot.stub(email = NEW_EMAIL))

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), NEW_EMAIL, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartEmailChange.Error.EmailAlreadyExist) { result.toString() }
        assertTrue(fixture.mailbox.sent.isEmpty())
    }

    @Test
    fun `should propagate a user service error other than not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val serverError = BusinessError(HttpStatusCode.InternalServerError.value, 0, "server error")
        coEvery { fixture.userClient.getUserByEmail(NEW_EMAIL) } returns Result.failure(serverError)

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), NEW_EMAIL, Language.EN)

        then()
        assertEquals(serverError, result.exceptionOrNull())
    }

    @Test
    fun `should return ResendTooSoon when a code was sent to the address within the cooldown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.emailIsFree(NEW_EMAIL)
        fixture.sut.invoke(userId, NEW_EMAIL, Language.EN).getOrThrow()

        whenn()
        val result = fixture.sut.invoke(userId, NEW_EMAIL, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.ResendTooSoon) { result.toString() }
    }

    @Test
    fun `should return Undeliverable when the address can't receive email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.emailIsFree(NEW_EMAIL)
        fixture.mailbox.refusesDelivery = true

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), NEW_EMAIL, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
    }

    private companion object {
        const val NEW_EMAIL = "new@example.com"
    }
}
