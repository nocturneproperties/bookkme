package com.bookk.auth.domain.impl.operation.telegram

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest
import com.bookk.auth.domain.api.telegram.entity.TelegramUser
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.auth.domain.impl.telegram.TelegramInitDataVerifier
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
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.operation.CreateUser
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SignInWithTelegramImplTest {

    private class SutFixture {
        val verifier = mockk<TelegramInitDataVerifier>()
        val telegramIdentityDataSource = mockk<TelegramIdentityDataSource>()
        val accountDataSource = mockk<AccountDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val userClient = mockk<UserClient>()
        val generateAuthToken = mockk<GenerateAuthToken>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val sut = SignInWithTelegramImpl(
            verifier,
            telegramIdentityDataSource,
            accountDataSource,
            deviceDataSource,
            userClient,
            generateAuthToken,
            eventProducer,
            transactionManager,
        )
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        val telegramUser = TelegramUser.stub()
        val request = TelegramSignInRequest.stub(
            acceptedDocuments = AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01"),
        )
        val deviceUuid = request.deviceInfo.deviceUUID

        init {
            transactionManager.mockTransaction()
            every { verifier.verify(request.initData) } returns telegramUser
            coEvery { generateAuthToken(any()) } returns Result.success(tokens)
        }

        fun MockKMatcherScope.initialAuthentication(authId: Uuid): GenerateAuthToken.Source {
            return match<GenerateAuthToken.Source.InitialAuthentication> {
                it.authId == authId && it.deviceUUID == deviceUuid
            }
        }

        fun existingAccount(account: Authentication) {
            coEvery { telegramIdentityDataSource.getAccountByTelegramUser(telegramUser.id) } returns account
        }

        fun newAccount(userId: Uuid) {
            coEvery { telegramIdentityDataSource.getAccountByTelegramUser(telegramUser.id) } returns null
            coEvery { userClient.createUser(any()) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(any()) } answers { firstArg() }
            coEvery { telegramIdentityDataSource.link(any(), telegramUser.id) } just runs
            coEvery { deviceDataSource.insertDevice(any(), deviceUuid, any(), any()) } returns Uuid.random()
        }
    }

    @Test
    fun `should sign an existing account in on a device it already knows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = Authentication.stub()
        with(fixture) {
            existingAccount(account)
            coEvery { deviceDataSource.insertDevice(account.id, deviceUuid, any(), Language.UK) } returns null
            coEvery { deviceDataSource.updateLanguage(account.id, deviceUuid, Language.UK) } just runs
        }

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.UK)

        then()
        assertEquals(fixture.tokens, result.getOrThrow())
        coVerify(exactly = 1) { fixture.generateAuthToken(with(fixture) { initialAuthentication(account.id) }) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AuthEvent.DeviceLanguageUpdated::class), any()) }
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should announce a new device of an existing account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = Authentication.stub()
        with(fixture) {
            existingAccount(account)
            coEvery { deviceDataSource.insertDevice(account.id, deviceUuid, any(), Language.EN) } returns Uuid.random()
        }

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AuthEvent.DeviceCreated::class), any()) }
        coVerify(exactly = 0) { fixture.deviceDataSource.updateLanguage(any(), any(), any()) }
    }

    @Test
    fun `should create an account with a placeholder email for an unknown Telegram user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val created = slot<CreateUserRequest>()
        val account = slot<Authentication>()
        with(fixture) {
            newAccount(userId)
            coEvery { userClient.createUser(capture(created)) } returns Result.success(userId)
            coEvery { accountDataSource.createAuthorization(capture(account)) } answers { firstArg() }
        }

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertEquals(fixture.tokens, result.getOrThrow())
        assertEquals(
            listOf("Ann", "Lee", "123456789@telegramundeliverable.org"),
            with(created.captured) { listOf(name, lastName, email) },
        )
        assertEquals(fixture.request.acceptedDocuments, created.captured.acceptedDocuments)
        assertEquals(123456789L, created.captured.telegramUserId)
        assertEquals(userId, account.captured.userId)
        coVerify(exactly = 1) { fixture.telegramIdentityDataSource.link(account.captured.id, 123456789) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AuthEvent.DeviceCreated::class), any()) }
        coVerify(exactly = 1) {
            fixture.generateAuthToken(with(fixture) { initialAuthentication(account.captured.id) })
        }
    }

    @Test
    fun `should create the account with an empty last name when Telegram has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = slot<CreateUserRequest>()
        with(fixture) {
            every { verifier.verify(request.initData) } returns TelegramUser.stub(lastName = null)
            newAccount(Uuid.random())
            coEvery { userClient.createUser(capture(created)) } returns Result.success(Uuid.random())
        }

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals("", created.captured.lastName)
    }

    @Test
    fun `should fail with LegalDocumentsNotAccepted without creating a user when no documents are sent`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val request = fixture.request.copy(acceptedDocuments = null)
            with(fixture) {
                every { verifier.verify(request.initData) } returns telegramUser
                coEvery { telegramIdentityDataSource.getAccountByTelegramUser(telegramUser.id) } returns null
            }

            whenn()
            val result = fixture.sut.invoke(request, Language.EN)

            then()
            assertTrue(result.exceptionOrNull() is CreateUser.Error.LegalDocumentsNotAccepted) { result.toString() }
            coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
            coVerify(exactly = 0) { fixture.accountDataSource.createAuthorization(any()) }
            coVerify(exactly = 0) { fixture.eventProducer.send(any(AuthEvent.UserDeleted::class), any()) }
        }

    @Test
    fun `should fail with LegalDocumentsNotAccepted when the user service rejects the sent documents`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            with(fixture) {
                newAccount(Uuid.random())
                coEvery { userClient.createUser(any()) } returns
                    Result.failure(CreateUser.Error.LegalDocumentsNotAccepted())
            }

            whenn()
            val result = fixture.sut.invoke(fixture.request, Language.EN)

            then()
            assertTrue(result.exceptionOrNull() is CreateUser.Error.LegalDocumentsNotAccepted) { result.toString() }
            coVerify(exactly = 0) { fixture.accountDataSource.createAuthorization(any()) }
            coVerify(exactly = 0) { fixture.eventProducer.send(any(AuthEvent.UserDeleted::class), any()) }
        }

    @Test
    fun `should fail with AccountCreationFailed and delete the new user when the device is not stored`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val userId = Uuid.random()
            val deleted = slot<AuthEvent.UserDeleted>()
            with(fixture) {
                newAccount(userId)
                coEvery { deviceDataSource.insertDevice(any(), deviceUuid, any(), any()) } returns null
                coEvery { eventProducer.send(capture(deleted), any()) } just runs
            }

            whenn()
            val result = fixture.sut.invoke(fixture.request, Language.EN)

            then()
            assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.AccountCreationFailed) {
                result.toString()
            }
            assertEquals(userId, deleted.captured.userId)
            coVerify(exactly = 0) { fixture.generateAuthToken(any()) }
        }

    @Test
    fun `should delete the new user when linking the Telegram user fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            newAccount(userId)
            coEvery { telegramIdentityDataSource.link(any(), telegramUser.id) } throws IllegalStateException("taken")
        }

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == userId }, any())
        }
    }

    @Test
    fun `should fail with TelegramDataInvalid without touching any account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        every { fixture.verifier.verify(fixture.request.initData) } throws
            SignInWithTelegram.Error.TelegramDataInvalid()

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
        coVerify(exactly = 0) { fixture.telegramIdentityDataSource.getAccountByTelegramUser(any()) }
        coVerify(exactly = 0) { fixture.userClient.createUser(any()) }
    }

    @Test
    fun `should fail with TelegramDataExpired without touching any account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        every { fixture.verifier.verify(fixture.request.initData) } throws
            SignInWithTelegram.Error.TelegramDataExpired()

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataExpired) { result.toString() }
        coVerify(exactly = 0) { fixture.telegramIdentityDataSource.getAccountByTelegramUser(any()) }
    }

    @Test
    fun `should fail with TelegramSignInUnavailable when no bot is configured`() = runUnitTest {
        given()
        val fixture = SutFixture()
        every { fixture.verifier.verify(fixture.request.initData) } throws
            SignInWithTelegram.Error.TelegramSignInUnavailable()

        whenn()
        val result = fixture.sut.invoke(fixture.request, Language.EN)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramSignInUnavailable) {
            result.toString()
        }
    }
}
