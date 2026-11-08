package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.api.email.operation.StartEmailChange
import com.bookk.auth.domain.api.error.AuthErrorCodes
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
import library.email.EmailCodeError
import library.email.EmailErrorCodes
import library.email.EmailSendError
import library.email.EmailVerificationStarted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class EmailChangeTest {

    private fun ApplicationTestBuilder.signedInAs(userId: Uuid, vararg useCases: Any) {
        setupApplication(
            extension = {
                install(Authentication) {
                    provider { authenticate { context -> context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) } }
                }
            },
            diModule = module {
                useCases.forEach { useCase ->
                    when (useCase) {
                        is StartEmailChange -> single { useCase }
                        is ConfirmEmailChange -> single { useCase }
                    }
                }
            },
            routeUnderTest = { emailChange() },
        )
    }

    private fun ApplicationTestBuilder.signedOut() {
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module {},
            routeUnderTest = { emailChange() },
        )
    }

    private suspend fun ApplicationTestBuilder.startRejectedWith(error: BusinessError): HttpResponse {
        val useCase: StartEmailChange = mockk()
        coEvery { useCase.invoke(any(), any(), any()) } returns Result.failure(error)
        signedInAs(Uuid.random(), useCase)
        return createTestClient().post(AuthRouting.Api.Auth.Email()) { setBody(EmailChangeRequest(NEW_EMAIL)) }
    }

    private suspend fun ApplicationTestBuilder.confirmRejectedWith(error: BusinessError): HttpResponse {
        val useCase: ConfirmEmailChange = mockk()
        coEvery { useCase.invoke(any(), any(), any()) } returns Result.failure(error)
        signedInAs(Uuid.random(), useCase)
        return createTestClient().post(AuthRouting.Api.Auth.Email.Confirm()) { setBody(EmailConfirmation(Uuid.random(), "123456")) }
    }

    @Test
    fun `should send a code to the new address of the caller in their language`() = routeTest {
        given()
        val useCase: StartEmailChange = mockk()
        val userId = Uuid.random()
        val started = EmailVerificationStarted.stub()
        coEvery { useCase.invoke(userId, NEW_EMAIL, Language.UK) } returns Result.success(started)
        signedInAs(userId, useCase)

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Email()) {
            header(HttpHeaders.AcceptLanguage, "uk-UA")
            setBody(EmailChangeRequest(NEW_EMAIL))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(started, response.body<EmailVerificationStarted>())
        coVerify(exactly = 1) { useCase.invoke(userId, NEW_EMAIL, Language.UK) }
    }

    @Test
    fun `should return unprocessable entity when the new address is taken`() = routeTest {
        given()
        val response = startRejectedWith(StartEmailChange.Error.EmailAlreadyExist())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.EMAIL_EXIST, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when the new address is malformed`() = routeTest {
        given()
        val response = startRejectedWith(StartEmailChange.Error.InvalidEmailFormat())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.INVALID_EMAIL_FORMAT, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when the new address can't receive email`() = routeTest {
        given()
        val response = startRejectedWith(EmailSendError.Undeliverable())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_UNDELIVERABLE, body.errorCode)
    }

    @Test
    fun `should return too many requests when a code was sent too recently`() = routeTest {
        given()
        val response = startRejectedWith(EmailCodeError.ResendTooSoon())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_RESEND_TOO_SOON, body.errorCode)
    }

    @Test
    fun `should return unauthorized when requesting a code signed out`() = routeTest {
        given()
        signedOut()

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Email()) { setBody(EmailChangeRequest(NEW_EMAIL)) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should store the new address when the caller confirms the code`() = routeTest {
        given()
        val useCase: ConfirmEmailChange = mockk()
        val userId = Uuid.random()
        val verificationId = Uuid.random()
        coEvery { useCase.invoke(userId, verificationId, "123456") } returns Result.success(Unit)
        signedInAs(userId, useCase)

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Email.Confirm()) { setBody(EmailConfirmation(verificationId, "123456")) }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, verificationId, "123456") }
    }

    @Test
    fun `should return unprocessable entity when the code is wrong`() = routeTest {
        given()
        val response = confirmRejectedWith(EmailCodeError.Invalid())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_INVALID, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when the code expired`() = routeTest {
        given()
        val response = confirmRejectedWith(EmailCodeError.Expired())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_EXPIRED, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity after too many wrong codes`() = routeTest {
        given()
        val response = confirmRejectedWith(EmailCodeError.AttemptsExceeded())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_ATTEMPTS_EXCEEDED, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when another account took the address meanwhile`() = routeTest {
        given()
        val response = confirmRejectedWith(ConfirmEmailChange.Error.EmailAlreadyExist())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.EMAIL_EXIST, body.errorCode)
    }

    @Test
    fun `should return unauthorized when confirming signed out`() = routeTest {
        given()
        signedOut()

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Email.Confirm()) { setBody(EmailConfirmation(Uuid.random(), "123456")) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private companion object {
        const val NEW_EMAIL = "new@example.com"
    }
}
