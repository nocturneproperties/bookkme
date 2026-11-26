package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationRequest
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.api.telegram.operation.TelegramConfirmationUnavailable
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.BusinessError
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
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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

internal class ConfirmActionWithTelegramTest {

    private val userId = Uuid.random()
    private val request = TelegramActionConfirmationRequest.stub()

    private fun ApplicationTestBuilder.signedIn(useCase: ConfirmActionWithTelegram) {
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
        val useCase: ConfirmActionWithTelegram = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(error)
        signedIn(useCase)
        return createTestClient().post(AuthRouting.Api.Auth.Telegram.ConfirmAction()) { setBody(request) }
    }

    @Test
    fun `should return the action confirmation token for the caller`() = routeTest {
        given()
        val useCase: ConfirmActionWithTelegram = mockk()
        coEvery { useCase.invoke(userId, request) } returns Result.success(ActionConfirmationToken("token"))
        signedIn(useCase)

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Telegram.ConfirmAction()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ActionConfirmationToken("token"), response.body<ActionConfirmationToken>())
    }

    @Test
    fun `should answer 422 for init data not signed by Telegram`() = routeTest {
        given()
        val error = SignInWithTelegram.Error.TelegramDataInvalid()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_DATA_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 for init data older than 5 minutes`() = routeTest {
        given()
        val error = SignInWithTelegram.Error.TelegramDataExpired()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_DATA_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 for an expired, used or mismatched confirmation`() = routeTest {
        given()
        val error = ConfirmActionWithTelegram.Error.TelegramConfirmationExpired()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_CONFIRMATION_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 for a launch from another Telegram account`() = routeTest {
        given()
        val error = ConfirmActionWithTelegram.Error.TelegramAccountMismatch()

        whenn()
        val response = rejectedWith(error)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.TELEGRAM_ACCOUNT_MISMATCH, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 503 when no bot id is set`() = routeTest {
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
        val useCase: ConfirmActionWithTelegram = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { telegramConfirmAction() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Telegram.ConfirmAction()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }
}
