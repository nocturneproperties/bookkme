package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.ChallengeWindowExpired
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.VerificationFailed
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.registration.operation.FinishRegistration.Error.AccountCreationFailed
import com.bookk.auth.domain.api.registration.operation.FinishRegistration.Error.InvalidEmailFormat
import com.bookk.auth.domain.api.registration.operation.FinishRegistration.Error.UserAlreadyExist
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
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

class PostValidateRegistrationTest {

    private fun createSimpleRequest(): VerifyAccountCreationRequest {
        return VerifyAccountCreationRequest(
            requestId = "user_id",
            deviceInfo = VerifyAccountCreationRequest.DeviceInfo(
                deviceUUID = Uuid.random(),
                deviceName = "example_device_name"
            ),
            userInfo = VerifyAccountCreationRequest.UserInfo(
                name = "name",
                lastName = "lastName",
                email = "email"
            ),
            publicKeyCredentialJson = "mock"
        )
    }

    @Test
    fun `should return unprocessable entity when email format is invalid`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(InvalidEmailFormat())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
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
    fun `should return unprocessable entity when user already exists`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(UserAlreadyExist())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.USER_ALREADY_EXIST, body.errorCode)
        assertEquals(UserAlreadyExist().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when challenge window is expired`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(ChallengeWindowExpired())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED, body.errorCode)
        assertEquals(ChallengeWindowExpired().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when verification fails`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(VerificationFailed())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.VERIFICATION_FAILED, body.errorCode)
        assertEquals(VerificationFailed().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when account creation fails`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(AccountCreationFailed())
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.ACCOUNT_CREATION_FAILED, body.errorCode)
        assertEquals(AccountCreationFailed().message, body.message)
    }

    @Test
    fun `should return OK and tokens when registration is completed`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        val expected = AuthTokens("access_token", "refresh_token")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        val body = response.body<AuthTokens>()
        then()
        coVerify { useCase.invoke(eq(request), any()) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, body)
    }

    @Test
    fun `should pass parsed language from Accept-Language header`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        val expected = AuthTokens("access_token", "refresh_token")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        client.post(AuthRouting.Api.Auth.SignUp()) {
            header(HttpHeaders.AcceptLanguage, "uk-UA,uk;q=0.9")
            setBody(request)
        }
        then()
        coVerify { useCase.invoke(eq(request), eq(Language.UK)) }
    }

    @Test
    fun `should default to EN when Accept-Language header is missing`() = routeTest {
        given()
        val useCase: FinishRegistration = mockk()
        val client = createTestClient()
        val request = createSimpleRequest()
        val expected = AuthTokens("access_token", "refresh_token")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(expected)
        setupApplication(
            diModule = module {
                single<FinishRegistration> { useCase }
            },
            routeUnderTest = { registration() }
        )
        whenn()
        client.post(AuthRouting.Api.Auth.SignUp()) {
            setBody(request)
        }
        then()
        coVerify { useCase.invoke(eq(request), eq(Language.EN)) }
    }
}
