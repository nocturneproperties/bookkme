package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import com.bookk.user.domain.api.operation.GetUserByEmail
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.email.PlaceholderEmail
import library.email.test.EmailTestMailbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class StartDemoUpgradeImplTest {

    private class SutFixture {
        val demoCallerResolver = mockk<DemoCallerResolver>()
        val userClient = mockk<UserClient>()
        val startPasskeyRegistration = mockk<StartPasskeyRegistration>()
        val mailbox = EmailTestMailbox()
        val sut = StartDemoUpgradeImpl(demoCallerResolver, userClient, startPasskeyRegistration, mailbox.issuer)
        val caller = DemoCaller.RefreshToken("refresh")
        val account = Authentication.stub()
        val challenge = RegistrationChallengeResponse.stub()

        init {
            coEvery { demoCallerResolver.resolve(caller) } returns DemoSession(account, Uuid.random())
            coEvery { userClient.getUserByEmail(any()) } returns Result.failure(GetUserByEmail.Error.UserNotFound())
            coEvery { startPasskeyRegistration(any(), any()) } returns Result.success(challenge)
        }
    }

    @Test
    fun `should start a passkey registration for the demo account's handle and mail a code`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(fixture.caller, "Jane Smith", EMAIL, Language.EN)

        then()
        assertEquals(fixture.challenge.requestId, result.getOrThrow().requestId)
        coVerify(exactly = 1) { fixture.startPasskeyRegistration(fixture.account.uuid, "Jane Smith") }
        assertTrue(fixture.mailbox.codeSentTo(EMAIL).isNotBlank())
    }

    @Test
    fun `should refuse an email that is not a valid address`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(fixture.caller, "Jane", "not an email", Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartDemoUpgrade.Error.InvalidEmailFormat) { result.toString() }
        coVerify(exactly = 0) { fixture.demoCallerResolver.resolve(any()) }
    }

    @Test
    fun `should refuse a placeholder email`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(
            fixture.caller,
            "Jane",
            PlaceholderEmail.forDemoUser(Uuid.random()),
            Language.EN,
        )

        then()
        assertTrue(result.exceptionOrNull() is StartDemoUpgrade.Error.EmailDomainUnsupported) { result.toString() }
    }

    @Test
    fun `should refuse an email another account already uses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userClient.getUserByEmail(EMAIL) } returns
            Result.success(UserSnapshot.stub(id = Uuid.random()))

        whenn()
        val result = fixture.sut.invoke(fixture.caller, "Jane", EMAIL, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is DemoUpgradeError.EmailAlreadyExist) { result.toString() }
        coVerify(exactly = 0) { fixture.startPasskeyRegistration(any(), any()) }
    }

    @Test
    fun `should accept the email the demo account already stored in an earlier attempt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.userClient.getUserByEmail(EMAIL)
        } returns Result.success(UserSnapshot.stub(id = fixture.account.userId))

        whenn()
        val result = fixture.sut.invoke(fixture.caller, "Jane", EMAIL, Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }

    @Test
    fun `should refuse a caller whose account is not a demo`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.demoCallerResolver.resolve(fixture.caller) } throws DemoUpgradeError.NotDemoAccount()

        whenn()
        val result = fixture.sut.invoke(fixture.caller, "Jane", EMAIL, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is DemoUpgradeError.NotDemoAccount) { result.toString() }
        coVerify(exactly = 0) { fixture.startPasskeyRegistration(any(), any()) }
    }

    companion object {
        private const val EMAIL = "jane@example.com"
    }
}
