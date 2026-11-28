package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.CreateUser
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module

internal class TelegramSignInTest {

    private fun requestWithDocuments(): TelegramSignInRequest {
        return TelegramSignInRequest.stub(
            initData = "auth_date=1&signature=abc&user=%7B%22id%22%3A1%7D",
            acceptedDocuments = AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01"),
        )
    }

    @Test
    fun `should return tokens and pass the request and language through`() = routeTest {
        given()
        val useCase: SignInWithTelegram = mockk()
        val request = requestWithDocuments()
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        coEvery { useCase.invoke(request, Language.UK) } returns Result.success(tokens)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { telegramSignIn() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Telegram.SignIn()) {
            header(HttpHeaders.AcceptLanguage, "uk")
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(tokens, response.body<AuthTokens>())
        coVerify(exactly = 1) { useCase.invoke(request, Language.UK) }
    }

    @Test
    fun `should accept a request without accepted documents`() = routeTest {
        given()
        val useCase: SignInWithTelegram = mockk()
        val request = TelegramSignInRequest.stub(acceptedDocuments = null)
        coEvery { useCase.invoke(request, any()) } returns Result.success(AuthTokens("access", "refresh"))
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { telegramSignIn() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Telegram.SignIn()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { useCase.invoke(request, any()) }
    }

    @Test
    fun `should answer TELEGRAM_DATA_INVALID for data Telegram did not sign`() = routeTest {
        given()
        failingWith(SignInWithTelegram.Error.TelegramDataInvalid())

        whenn()
        val response = postSignIn()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_DATA_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer TELEGRAM_DATA_EXPIRED for data that is too old`() = routeTest {
        given()
        failingWith(SignInWithTelegram.Error.TelegramDataExpired())

        whenn()
        val response = postSignIn()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_DATA_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer ACCOUNT_CREATION_FAILED when the account could not be stored`() = routeTest {
        given()
        failingWith(SignInWithTelegram.Error.AccountCreationFailed())

        whenn()
        val response = postSignIn()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.ACCOUNT_CREATION_FAILED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer LEGAL_DOCUMENTS_NOT_ACCEPTED for a new user who accepted nothing`() = routeTest {
        given()
        failingWith(CreateUser.Error.LegalDocumentsNotAccepted())

        whenn()
        val response = postSignIn()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENTS_NOT_ACCEPTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer LEGAL_DOCUMENT_VERSION_UNKNOWN for an unpublished version`() = routeTest {
        given()
        failingWith(LegalDocumentError.VersionUnknown())

        whenn()
        val response = postSignIn()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 503 TELEGRAM_SIGN_IN_UNAVAILABLE when no bot is configured`() = routeTest {
        given()
        failingWith(SignInWithTelegram.Error.TelegramSignInUnavailable())

        whenn()
        val response = postSignIn()

        then()
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_SIGN_IN_UNAVAILABLE, response.body<SimpleServerError>().errorCode)
    }

    private fun ApplicationTestBuilder.failingWith(error: Throwable) {
        val useCase: SignInWithTelegram = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(error)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { telegramSignIn() })
    }

    private suspend fun ApplicationTestBuilder.postSignIn(): HttpResponse {
        return createTestClient().post(AuthRouting.Api.Auth.Telegram.SignIn()) { setBody(requestWithDocuments()) }
    }
}
