package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.RefreshToken
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
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module

internal class RefreshTokenTest {

    @Test
    fun `should return a new token pair for the refresh token in the authorization header`() = routeTest {
        given()
        val useCase: RefreshToken = mockk()
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        coEvery { useCase.invoke("old-refresh") } returns Result.success(tokens)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postRefreshToken() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Refresh()) {
            header(HttpHeaders.Authorization, "Bearer old-refresh")
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(tokens, response.body<AuthTokens>())
    }

    @Test
    fun `should answer 401 without a refresh token`() = routeTest {
        given()
        val useCase: RefreshToken = mockk()
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postRefreshToken() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Refresh())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { useCase.invoke(any()) }
    }

    @Test
    fun `should answer 401 DEMO_EXPIRED once the demo of the account has ended`() = routeTest {
        given()
        val useCase: RefreshToken = mockk()
        coEvery { useCase.invoke(any()) } returns Result.failure(GenerateAuthToken.Error.DemoExpired())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postRefreshToken() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Refresh()) {
            header(HttpHeaders.Authorization, "Bearer demo-refresh")
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCodes.DEMO_EXPIRED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer 401 INVALID_CREDENTIALS for an unknown refresh token`() = routeTest {
        given()
        val useCase: RefreshToken = mockk()
        coEvery { useCase.invoke(any()) } returns Result.failure(GenerateAuthToken.Error.InvalidCredentials())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postRefreshToken() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Refresh()) {
            header(HttpHeaders.Authorization, "Bearer unknown")
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCodes.INVALID_CREDENTIALS, response.body<SimpleServerError>().errorCode)
    }
}
