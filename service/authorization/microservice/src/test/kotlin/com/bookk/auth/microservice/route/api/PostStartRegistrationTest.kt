package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.EmailAlreadyExist
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.InvalidEmailFormat
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
import library.email.EmailCodeError
import library.email.EmailErrorCodes
import library.email.EmailSendError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module

class PostStartRegistrationTest {

    @Test
    fun `should return unprocessable entity when email format is invalid`() = routeTest {
        given()
        val useCase: StartRegistration = mockk()
        val client = createTestClient()
        val request = CreateAccountRequest("firstName", "lastName", "email")
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(InvalidEmailFormat())
        setupApplication(
            diModule = module {
                single<StartRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )

        whenn()
        val response = client.post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()

        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.INVALID_EMAIL_FORMAT, body.errorCode)
        assertEquals(InvalidEmailFormat().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when email already exists`() = routeTest {
        given()
        val useCase: StartRegistration = mockk()
        val client = createTestClient()
        val request = CreateAccountRequest("firstName", "lastName", "email")
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(EmailAlreadyExist())
        setupApplication(
            diModule = module {
                single<StartRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.EMAIL_EXIST, body.errorCode)
        assertEquals(EmailAlreadyExist().message, body.message)
    }

    @Test
    fun `should return OK when registration challenge is successfully started`() = routeTest {
        given()
        val useCase: StartRegistration = mockk()
        val client = createTestClient()
        val request = CreateAccountRequest("firstName", "lastName", "email")
        val expected = SignUpChallengeResponse.stub()
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<StartRegistration> { useCase }
            },
            routeUnderTest = { registration() },
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(request)
        }
        val actual = response.body<SignUpChallengeResponse>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, actual)
    }

    private suspend fun ApplicationTestBuilder.rejectedWith(error: BusinessError): HttpResponse {
        val useCase: StartRegistration = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(error)
        setupApplication(
            diModule = module { single<StartRegistration> { useCase } },
            routeUnderTest = { registration() },
        )
        return createTestClient().post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(CreateAccountRequest("firstName", "lastName", "email@example.com"))
        }
    }

    @Test
    fun `should return too many requests when a code was sent to the address too recently`() = routeTest {
        given()
        val response = rejectedWith(EmailCodeError.ResendTooSoon())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(EmailErrorCodes.EMAIL_CODE_RESEND_TOO_SOON, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when the address can't receive email`() = routeTest {
        given()
        val response = rejectedWith(EmailSendError.Undeliverable())

        whenn()
        val body = response.body<SimpleServerError>()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(EmailErrorCodes.EMAIL_UNDELIVERABLE, body.errorCode)
    }

    @Test
    fun `should write the code email in the language from Accept-Language`() = routeTest {
        given()
        val useCase: StartRegistration = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.success(SignUpChallengeResponse.stub())
        setupApplication(
            diModule = module { single<StartRegistration> { useCase } },
            routeUnderTest = { registration() },
        )

        whenn()
        createTestClient().post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            header(HttpHeaders.AcceptLanguage, "pl-PL")
            setBody(CreateAccountRequest("firstName", "lastName", "email@example.com"))
        }

        then()
        coVerify(exactly = 1) { useCase.invoke(any(), Language.PL) }
    }
}
