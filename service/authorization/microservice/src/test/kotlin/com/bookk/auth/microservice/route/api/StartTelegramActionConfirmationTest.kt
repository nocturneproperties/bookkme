package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationStart
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation.Error
import com.bookk.auth.domain.api.telegram.operation.TelegramConfirmationUnavailable
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class StartTelegramActionConfirmationTest {

    private val userId = Uuid.random()
    private val request = TelegramActionConfirmationStart(ConfirmableAction.DELETE_ACCOUNT, Uuid.NIL, userId)

    private fun ApplicationTestBuilder.signedIn(useCase: StartTelegramActionConfirmation) {
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { telegramConfirmAction() },
        )
    }

    private suspend fun ApplicationTestBuilder.rejectedWith(error: BusinessError): HttpResponse {
        val useCase: StartTelegramActionConfirmation = mockk()
        coEvery { useCase.invoke(any(), any(), any()) } returns Result.failure(error)
        signedIn(useCase)
        return createTestClient().post(AuthRouting.Api.Auth.Telegram.ConfirmAction.Start()) { setBody(request) }
    }

    @Test
    fun `should send the confirmation link in the request's language and answer no content`() = routeTest {
        given()
        val useCase: StartTelegramActionConfirmation = mockk()
        coEvery { useCase.invoke(userId, request, Language.PL) } returns Result.success(Unit)
        signedIn(useCase)

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Telegram.ConfirmAction.Start()) {
            header(HttpHeaders.AcceptLanguage, "pl-PL")
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, request, Language.PL) }
    }

    @Test
    fun `should answer 422 for an action not confirmable through Telegram`() = routeTest {
        given()
        val error = Error.ActionNotConfirmableThroughTelegram()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            AuthErrorCodes.ACTION_NOT_CONFIRMABLE_THROUGH_TELEGRAM,
            response.body<SimpleServerError>().errorCode,
        )
    }

    @Test
    fun `should answer 422 for an account without a Telegram identity`() = routeTest {
        given()
        val error = Error.TelegramIdentityMissing()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_IDENTITY_MISSING, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 429 for a start within the cooldown`() = routeTest {
        given()
        val error = Error.TelegramConfirmationTooFrequent()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_CONFIRMATION_TOO_FREQUENT, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 when the bot can't write to the user`() = routeTest {
        given()
        val error = Error.TelegramChatUnreachable()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_CHAT_UNREACHABLE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 503 when the bot is not set up or failed to send`() = routeTest {
        given()
        val error = TelegramConfirmationUnavailable()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_CONFIRMATION_UNAVAILABLE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 401 without a valid access token`() = routeTest {
        given()
        val useCase: StartTelegramActionConfirmation = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { telegramConfirmAction() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Telegram.ConfirmAction.Start()) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any(), any()) }
    }
}
