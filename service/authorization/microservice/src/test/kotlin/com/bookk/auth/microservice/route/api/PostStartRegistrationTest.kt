package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.EmailAlreadyExist
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.InvalidEmailFormat
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
        coEvery { useCase.invoke(any()) } returns Result.failure(InvalidEmailFormat())
        setupApplication(
            diModule = module {
                single<StartRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )

        whenn()
        val response = client.post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()

        then()
        coVerify { useCase.invoke(eq(request)) }
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
        coEvery { useCase.invoke(any()) } returns Result.failure(EmailAlreadyExist())
        setupApplication(
            diModule = module {
                single<StartRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request)) }
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
        val expected = RegistrationChallengeResponse("example_challenge", "display_name", "challenge", "userId", "Name")
        coEvery { useCase.invoke(any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<StartRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.PassKey.SignUpChallenge()) {
            setBody(request)
        }
        val actual = response.body<RegistrationChallengeResponse>()
        then()
        coVerify { useCase.invoke(eq(request)) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, actual)
    }
}