package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.api.demo.entity.DemoStartRequest
import com.bookk.auth.domain.api.demo.operation.StartDemo
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
import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.user.domain.api.operation.CreateUser
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import library.email.PlaceholderEmail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class StartDemoImplTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val userClient = mockk<UserClient>()
        val generateAuthToken = mockk<GenerateAuthToken>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val meterRegistry = SimpleMeterRegistry()
        val sut = StartDemoImpl(
            accountDataSource,
            deviceDataSource,
            userClient,
            generateAuthToken,
            eventProducer,
            transactionManager,
            meterRegistry,
        )
        val request = DemoStartRequest.stub()
        val userId = Uuid.random()
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        val createdUser = slot<CreateUserRequest>()
        val createdAccount = slot<Authentication>()

        init {
            transactionManager.mockTransaction()
            coEvery { accountDataSource.countDemoAccountsActiveAt(any()) } returns 0
            coEvery { userClient.createUser(capture(createdUser)) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(capture(createdAccount)) } answers { firstArg() }
            coEvery { deviceDataSource.insertDevice(any(), any(), any(), any()) } returns Uuid.random()
            coEvery { generateAuthToken(any()) } returns Result.success(tokens)
        }

        fun startedDemos(): Double {
            return meterRegistry.find(StartDemoImpl.DEMO_STARTED_METRIC).counter()?.count() ?: 0.0
        }
    }

    @Test
    fun `should create a demo account with a placeholder user and return its tokens`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val before = Clock.System.now()

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.PL)

        then()
        assertEquals(fixture.tokens, result.getOrNull())
        assertTrue(PlaceholderEmail.isPlaceholder(fixture.createdUser.captured.email))
        assertEquals(fixture.request.acceptedDocuments, fixture.createdUser.captured.acceptedDocuments)
        assertEquals(fixture.userId, fixture.createdAccount.captured.userId)
        val expiresAt = requireNotNull(fixture.createdAccount.captured.demoExpiresAt)
        val earliest = before + DemoAccountPolicy.LENGTH
        assertTrue(expiresAt >= earliest && expiresAt <= earliest + 1.minutes) { expiresAt.toString() }
        assertEquals(1.0, fixture.startedDemos())
    }

    @Test
    fun `should register the device and sign it in with the device uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = fixture.request.deviceInfo.deviceUUID

        whenn()
        fixture.sut.invoke(fixture.request, Language.PL)

        then()
        coVerify(exactly = 1) {
            fixture.deviceDataSource.insertDevice(any(), deviceUuid, fixture.request.deviceInfo.deviceName, Language.PL)
        }
        coVerify(exactly = 1) {
            fixture.generateAuthToken(
                match<GenerateAuthToken.Source.InitialAuthentication> { it.deviceUUID == deviceUuid },
            )
        }
    }

    @Test
    fun `should announce the new device and the demo with its time zone and language`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.invoke(fixture.request, Language.PL)

        then()
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AuthEvent.DeviceCreated::class), any()) }
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<AuthEvent.DemoAccountCreated> {
                    it.userId == fixture.userId &&
                        it.timeZoneId == fixture.request.timeZone &&
                        it.language == Language.PL &&
                        it.expiresAt == fixture.createdAccount.captured.demoExpiresAt
                },
                any(),
            )
        }
    }

    @Test
    fun `should refuse a new demo once the active demo accounts reach the cap`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.accountDataSource.countDemoAccountsActiveAt(any())
        } returns DemoAccountPolicy.MAX_ACTIVE_ACCOUNTS.toLong()

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartDemo.Error.DemoUnavailable) { result.toString() }
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
        assertEquals(0.0, fixture.startedDemos())
    }

    @Test
    fun `should pass a refused legal document acceptance through without creating an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userClient.createUser(any()) } returns
            Result.failure(CreateUser.Error.LegalDocumentsNotAccepted())

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is CreateUser.Error.LegalDocumentsNotAccepted) { result.toString() }
        coVerify(exactly = 0) { fixture.accountDataSource.createAuthorization(any()) }
    }

    @Test
    fun `should delete the created user again when the device cannot be stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deviceDataSource.insertDevice(any(), any(), any(), any()) } returns null

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is StartDemo.Error.AccountCreationFailed) { result.toString() }
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == fixture.userId }, any())
        }
        assertEquals(0.0, fixture.startedDemos())
    }

    @Test
    fun `should delete the created user again when the tokens cannot be issued`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.generateAuthToken(any()) } returns
            Result.failure(GenerateAuthToken.Error.InvalidCredentials())

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == fixture.userId }, any())
        }
    }
}
