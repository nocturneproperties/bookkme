package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.DemoAccount
import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeFinishRequest
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.datasource.AccountDataSource
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
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.error.UserErrorCodes
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import library.email.EmailCodeError
import library.email.test.EmailTestMailbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class FinishDemoUpgradeImplTest {

    private class SutFixture {
        val demoCallerResolver = mockk<DemoCallerResolver>()
        val accountDataSource = mockk<AccountDataSource>(relaxUnitFun = true)
        val userClient = mockk<UserClient>()
        val finishPasskeyRegistration = mockk<FinishPasskeyRegistration>(relaxUnitFun = true)
        val mailbox = EmailTestMailbox()
        val generateAuthToken = mockk<GenerateAuthToken>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val meterRegistry = SimpleMeterRegistry()
        val sut = FinishDemoUpgradeImpl(
            demoCallerResolver,
            accountDataSource,
            userClient,
            finishPasskeyRegistration,
            mailbox.redeemer,
            generateAuthToken,
            eventProducer,
            transactionManager,
            meterRegistry,
        )
        val caller = DemoCaller.AccessToken(Uuid.random(), Uuid.random())
        val account = Authentication.stub(demoExpiresAt = Clock.System.now() + 10.days)
        val deviceUuid = Uuid.random()
        val passkey = PasskeyCredential.stub(authInfo = account)
        val tokens = AuthTokens("access", "refresh")
        val sentInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { demoCallerResolver.resolve(caller) } returns DemoSession(account, deviceUuid)
            coEvery { finishPasskeyRegistration.verifyRequest(any()) } returns Result.success(passkey)
            coEvery { userClient.changeEmailAndName(any(), any(), any()) } returns Result.success(Unit)
            coEvery {
                accountDataSource.getDemoAccountForUpdate(account.id)
            } returns
                DemoAccount(account.id, account.userId, requireNotNull(account.demoExpiresAt), deletionHold = false)
            coEvery { generateAuthToken(any()) } returns Result.success(tokens)
            coEvery { eventProducer.send(any<AuthEvent.DemoUpgraded>(), any()) } coAnswers {
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        suspend fun request(purpose: String = EmailPurpose.DEMO_UPGRADE): DemoUpgradeFinishRequest {
            val requestId = Uuid.random()
            val started = mailbox.issuer.issue(EMAIL, purpose, requestId) { AuthEmails.signUpCode(Language.EN, it) }
                .getOrThrow()
            return DemoUpgradeFinishRequest.stub(
                requestId = requestId.toString(),
                verificationId = started.verificationId,
                emailCode = mailbox.codeSentTo(EMAIL),
                name = "Jane Smith",
            )
        }

        fun upgradedDemos(): Double {
            return meterRegistry.find(FinishDemoUpgradeImpl.DEMO_UPGRADED_METRIC).counter()?.count() ?: 0.0
        }
    }

    @Test
    fun `should end the demo, keep the passkey and sign the device in again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.request()

        whenn()
        val result = fixture.sut.invoke(fixture.caller, request)

        then()
        assertEquals(fixture.tokens, result.getOrThrow())
        coVerifyOrder {
            fixture.accountDataSource.getDemoAccountForUpdate(fixture.account.id)
            fixture.accountDataSource.endDemo(fixture.account.id)
            fixture.finishPasskeyRegistration.attachOwner(fixture.account.id, fixture.passkey)
            fixture.generateAuthToken(
                match<GenerateAuthToken.Source.InitialAuthentication> { it.deviceUUID == fixture.deviceUuid },
            )
        }
        assertEquals(1.0, fixture.upgradedDemos())
    }

    @Test
    fun `should replace the placeholder email and name with the verified ones`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.invoke(fixture.caller, fixture.request())

        then()
        coVerify(exactly = 1) {
            fixture.userClient.changeEmailAndName(fixture.account.userId, EMAIL, ProfileName("Jane", "Smith"))
        }
    }

    @Test
    fun `should announce the upgrade inside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.invoke(fixture.caller, fixture.request())

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.DemoUpgraded> { it.userId == fixture.account.userId }, any())
        }
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should upgrade by refresh token in the grace period after the demo ended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val caller = DemoCaller.RefreshToken("refresh")
        val ended = Clock.System.now() - 3.days
        coEvery { fixture.demoCallerResolver.resolve(caller) } returns DemoSession(fixture.account, fixture.deviceUuid)
        coEvery {
            fixture.accountDataSource.getDemoAccountForUpdate(fixture.account.id)
        } returns DemoAccount(fixture.account.id, fixture.account.userId, ended, deletionHold = false)

        whenn()
        val result = fixture.sut.invoke(caller, fixture.request())

        then()
        assertEquals(fixture.tokens, result.getOrThrow())
        coVerify(exactly = 1) { fixture.accountDataSource.endDemo(fixture.account.id) }
    }

    @Test
    fun `should refuse a code issued for another purpose`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = fixture.request(purpose = EmailPurpose.SIGN_UP)

        whenn()
        val result = fixture.sut.invoke(fixture.caller, request)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Invalid) { result.toString() }
        coVerify(exactly = 0) { fixture.accountDataSource.endDemo(any()) }
    }

    @Test
    fun `should refuse a passkey registered for another account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.finishPasskeyRegistration.verifyRequest(any()) } returns
            Result.success(PasskeyCredential.stub())

        whenn()
        val result = fixture.sut.invoke(fixture.caller, fixture.request())

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.VerificationFailed) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.changeEmailAndName(any(), any(), any()) }
    }

    @Test
    fun `should answer EMAIL_EXIST when another account took the address meanwhile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userClient.changeEmailAndName(any(), any(), any()) } returns Result.failure(
            object : BusinessError(422, UserErrorCodes.USER_CONTACT_TAKEN, "taken") {},
        )

        whenn()
        val result = fixture.sut.invoke(fixture.caller, fixture.request())

        then()
        assertTrue(result.exceptionOrNull() is DemoUpgradeError.EmailAlreadyExist) { result.toString() }
        coVerify(exactly = 0) { fixture.accountDataSource.endDemo(any()) }
    }

    @Test
    fun `should refuse an account that was upgraded meanwhile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.accountDataSource.getDemoAccountForUpdate(fixture.account.id) } returns null

        whenn()
        val result = fixture.sut.invoke(fixture.caller, fixture.request())

        then()
        assertTrue(result.exceptionOrNull() is DemoUpgradeError.NotDemoAccount) { result.toString() }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DemoUpgraded>(), any()) }
        assertEquals(0.0, fixture.upgradedDemos())
    }

    @Test
    fun `should refuse the upgrade once the grace period has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.accountDataSource.getDemoAccountForUpdate(fixture.account.id)
        } returns DemoAccount(fixture.account.id, fixture.account.userId, Clock.System.now() - 8.days, false)

        whenn()
        val result = fixture.sut.invoke(fixture.caller, fixture.request())

        then()
        assertTrue(result.exceptionOrNull() is GenerateAuthToken.Error.DemoExpired) { result.toString() }
        coVerify(exactly = 0) { fixture.accountDataSource.endDemo(any()) }
    }

    @Test
    fun `should refuse a request id that is not a uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(fixture.caller, DemoUpgradeFinishRequest.stub(requestId = "nope"))

        then()
        assertTrue(result.exceptionOrNull() is FinishPasskeyRegistration.Error.ChallengeWindowExpired) {
            result.toString()
        }
    }

    companion object {
        private const val EMAIL = "jane@example.com"
    }
}
