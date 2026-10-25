package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.authentication.entity.VerifySignInRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion.Error.ChallengeWindowExpired
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion.Error.PasskeyOwnerNotFound
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion.Error.VerificationFailed
import com.bookk.auth.domain.api.authentication.operation.SignIn
import com.bookk.auth.domain.api.error.AuthErrorCodes
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

internal class PostValidateSignInTest {

    private fun createSimpleRequest(deviceId: Uuid = Uuid.random()): VerifySignInRequest = VerifySignInRequest(
        requestId = Uuid.random().toString(),
        deviceInfo = VerifySignInRequest.DeviceInfo(
            deviceUUID = deviceId,
            deviceName = ""
        ),
        publicKeyCredentialJson = "mock"
    )

    @Test
    fun `should return OK and tokens when sign in is successful`() = routeTest {
        given()
        val useCase: SignIn = mockk()
        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val answer = AuthTokens(accessToken = "token1", refreshToken = "token2")
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.success(answer)

        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignIn()) {
            setBody(request)
        }
        val body = response.body<AuthTokens>()

        then()
        coVerify { useCase.invoke(any(), any()) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(answer, body)
    }

    @Test
    fun `should return unprocessable entity when passkey owner is not found`() = routeTest {
        given()
        val useCase: SignIn = mockk()
        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(PasskeyOwnerNotFound())

        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignIn()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(any(), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.PASSKEY_OWNER_NOT_FOUND, body.errorCode)
        assertEquals(PasskeyOwnerNotFound().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when challenge window is expired`() = routeTest {
        given()
        val useCase: SignIn = mockk()
        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(ChallengeWindowExpired())

        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignIn()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(any(), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED, body.errorCode)
        assertEquals(ChallengeWindowExpired().message, body.message)
    }

    @Test
    fun `should return unprocessable entity when verification fails`() = routeTest {
        given()
        val useCase: SignIn = mockk()
        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val request = createSimpleRequest()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(VerificationFailed())

        whenn()
        val response = client.post(AuthRouting.Api.Auth.SignIn()) {
            setBody(request)
        }
        val body = response.body<SimpleServerError>()
        then()
        coVerify { useCase.invoke(any(), any()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.VERIFICATION_FAILED, body.errorCode)
        assertEquals(VerificationFailed().message, body.message)
    }

    @Test
    fun `should pass parsed language from Accept-Language header`() = routeTest {
        given()
        val useCase: SignIn = mockk()
        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val request = createSimpleRequest()
        val answer = AuthTokens(accessToken = "token1", refreshToken = "token2")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(answer)

        whenn()
        client.post(AuthRouting.Api.Auth.SignIn()) {
            header(HttpHeaders.AcceptLanguage, "uk-UA,uk;q=0.9")
            setBody(request)
        }

        then()
        coVerify { useCase.invoke(eq(request), eq(Language.UK)) }
    }

    @Test
    fun `should default to EN when Accept-Language header is missing`() = routeTest {
        given()
        val useCase: SignIn = mockk()
        setupApplication(
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val request = createSimpleRequest()
        val answer = AuthTokens(accessToken = "token1", refreshToken = "token2")
        coEvery { useCase.invoke(any(), any()) } returns Result.success(answer)

        whenn()
        client.post(AuthRouting.Api.Auth.SignIn()) {
            setBody(request)
        }

        then()
        coVerify { useCase.invoke(eq(request), eq(Language.EN)) }
    }
}
