package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeFinishRequest
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeStartRequest
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError
import com.bookk.auth.domain.api.demo.operation.FinishDemoUpgrade
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.microservice.route.AuthRouting
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class DemoUpgradeTest {

    private class Operations {
        val start: StartDemoUpgrade = mockk()
        val finish: FinishDemoUpgrade = mockk()
    }

    private val authId = Uuid.random()
    private val deviceId = Uuid.random()

    private fun ApplicationTestBuilder.setup(operations: Operations, signedIn: Boolean) {
        val principal = AppPrincipal(authId, Uuid.random(), deviceId, isDemo = true)
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context -> if (signedIn) context.principal(principal) }
                    }
                }
            },
            diModule = module {
                single { operations.start }
                single { operations.finish }
            },
            routeUnderTest = { demoUpgrade() },
        )
    }

    private suspend fun ApplicationTestBuilder.postStart(body: DemoUpgradeStartRequest): HttpResponse {
        return createTestClient().post(AuthRouting.Api.Auth.Demo.UpgradeChallenge()) {
            header(HttpHeaders.AcceptLanguage, "pl")
            setBody(body)
        }
    }

    private suspend fun ApplicationTestBuilder.postFinish(body: DemoUpgradeFinishRequest): HttpResponse {
        return createTestClient().post(AuthRouting.Api.Auth.Demo.Upgrade()) { setBody(body) }
    }

    @Test
    fun `should start the upgrade of the signed in demo account`() = routeTest {
        given()
        val operations = Operations()
        val challenge = SignUpChallengeResponse.stub()
        coEvery {
            operations.start.invoke(any<DemoCaller.AccessToken>(), "Jane Smith", "jane@example.com", Language.PL)
        } returns Result.success(challenge)
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(challenge, response.body<SignUpChallengeResponse>())
        coVerify(exactly = 1) {
            operations.start.invoke(
                match<DemoCaller.AccessToken> { it.authId == authId && it.deviceId == deviceId },
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `should start the upgrade with the refresh token when no access token is sent`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.success(SignUpChallengeResponse.stub())
        setup(operations, signedIn = false)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub(refreshToken = "refresh"))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) {
            operations.start.invoke(match<DemoCaller.RefreshToken> { it.token == "refresh" }, any(), any(), any())
        }
    }

    @Test
    fun `should answer 401 to a start without an access token or a refresh token`() = routeTest {
        given()
        val operations = Operations()
        setup(operations, signedIn = false)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { operations.start.invoke(any(), any(), any(), any()) }
    }

    @Test
    fun `should answer 401 to a start with an invalid access token`() = routeTest {
        given()
        val operations = Operations()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { operations.start } },
            routeUnderTest = { demoUpgrade() },
        )

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Demo.UpgradeChallenge()) {
            header(HttpHeaders.Authorization, "Bearer expired")
            setBody(DemoUpgradeStartRequest.stub(refreshToken = "refresh"))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { operations.start.invoke(any(), any(), any(), any()) }
    }

    @Test
    fun `should finish the upgrade of the signed in demo account`() = routeTest {
        given()
        val operations = Operations()
        val tokens = AuthTokens("access", "refresh")
        val request = DemoUpgradeFinishRequest.stub()
        coEvery { operations.finish.invoke(any<DemoCaller.AccessToken>(), request) } returns Result.success(tokens)
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(request)

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(tokens, response.body<AuthTokens>())
    }

    @Test
    fun `should finish the upgrade with the refresh token in the grace period`() = routeTest {
        given()
        val operations = Operations()
        val request = DemoUpgradeFinishRequest.stub(refreshToken = "refresh")
        coEvery {
            operations.finish.invoke(match<DemoCaller.RefreshToken> { it.token == "refresh" }, request)
        } returns Result.success(AuthTokens("access", "refresh"))
        setup(operations, signedIn = false)

        whenn()
        val response = postFinish(request)

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should answer 401 to a finish without an access token or a refresh token`() = routeTest {
        given()
        val operations = Operations()
        setup(operations, signedIn = false)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { operations.finish.invoke(any(), any()) }
    }

    @Test
    fun `should answer 422 EMAIL_EXIST when starting the upgrade with an email another account uses`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.failure(DemoUpgradeError.EmailAlreadyExist())
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.EMAIL_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 INVALID_EMAIL_FORMAT when starting the upgrade with an invalid email`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.failure(StartDemoUpgrade.Error.InvalidEmailFormat())
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.INVALID_EMAIL_FORMAT, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 EMAIL_DOMAIN_UNSUPPORTED when starting the upgrade with a placeholder email`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.failure(StartDemoUpgrade.Error.EmailDomainUnsupported())
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.EMAIL_DOMAIN_UNSUPPORTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 NOT_A_DEMO_ACCOUNT when starting the upgrade with an account that is not a demo`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.failure(DemoUpgradeError.NotDemoAccount())
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.NOT_A_DEMO_ACCOUNT, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 401 INVALID_CREDENTIALS when starting the upgrade with an unknown refresh token`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.failure(GenerateAuthToken.Error.InvalidCredentials())
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(401, response.status.value)
        assertEquals(AuthErrorCodes.INVALID_CREDENTIALS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 401 DEMO_EXPIRED when starting the upgrade with a demo past its grace period`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.start.invoke(any(), any(), any(), any()) } returns
            Result.failure(GenerateAuthToken.Error.DemoExpired())
        setup(operations, signedIn = true)

        whenn()
        val response = postStart(DemoUpgradeStartRequest.stub())

        then()
        assertEquals(401, response.status.value)
        assertEquals(AuthErrorCodes.DEMO_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 EMAIL_EXIST when finishing the upgrade with an email taken meanwhile`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns Result.failure(DemoUpgradeError.EmailAlreadyExist())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.EMAIL_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 VERIFICATION_FAILED when finishing the upgrade with a passkey that fails verification`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns
            Result.failure(FinishPasskeyRegistration.Error.VerificationFailed())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.VERIFICATION_FAILED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 CHALLENGE_WINDOW_EXPIRED when finishing the upgrade with an expired passkey challenge`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns
            Result.failure(FinishPasskeyRegistration.Error.ChallengeWindowExpired())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 NOT_A_DEMO_ACCOUNT when finishing the upgrade with an account upgraded meanwhile`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns Result.failure(DemoUpgradeError.NotDemoAccount())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(AuthErrorCodes.NOT_A_DEMO_ACCOUNT, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 EMAIL_CODE_INVALID when finishing the upgrade with a wrong email code`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns Result.failure(EmailCodeError.Invalid())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(EmailErrorCodes.EMAIL_CODE_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 EMAIL_CODE_EXPIRED when finishing the upgrade with an expired email code`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns Result.failure(EmailCodeError.Expired())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(EmailErrorCodes.EMAIL_CODE_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 422 EMAIL_CODE_ATTEMPTS_EXCEEDED when finishing the upgrade with too many wrong email codes`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns Result.failure(EmailCodeError.AttemptsExceeded())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(422, response.status.value)
        assertEquals(EmailErrorCodes.EMAIL_CODE_ATTEMPTS_EXCEEDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 401 INVALID_CREDENTIALS when finishing the upgrade with an unknown refresh token`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns
            Result.failure(GenerateAuthToken.Error.InvalidCredentials())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(401, response.status.value)
        assertEquals(AuthErrorCodes.INVALID_CREDENTIALS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 401 DEMO_EXPIRED when finishing the upgrade with a demo past its grace period`() = routeTest {
        given()
        val operations = Operations()
        coEvery { operations.finish.invoke(any(), any()) } returns Result.failure(GenerateAuthToken.Error.DemoExpired())
        setup(operations, signedIn = true)

        whenn()
        val response = postFinish(DemoUpgradeFinishRequest.stub())

        then()
        assertEquals(401, response.status.value)
        assertEquals(AuthErrorCodes.DEMO_EXPIRED, response.body<SimpleServerError>().errorCode)
    }
}
