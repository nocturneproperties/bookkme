package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.demo.entity.DemoStartRequest
import com.bookk.auth.domain.api.demo.operation.StartDemo
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.DemoBusinessKind
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
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

internal class DemoStartTest {

    @Test
    fun `should return the demo account's tokens and pass the request and language through`() = routeTest {
        given()
        val useCase: StartDemo = mockk()
        val request = DemoStartRequest.stub()
        val tokens = AuthTokens(accessToken = "access", refreshToken = "refresh")
        coEvery { useCase.invoke(request, Language.UK) } returns Result.success(tokens)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { demo() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Demo()) {
            header(HttpHeaders.AcceptLanguage, "uk")
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(tokens, response.body<AuthTokens>())
        coVerify(exactly = 1) { useCase.invoke(request, Language.UK) }
    }

    @Test
    fun `should pass the chosen sample business kind through`() = routeTest {
        given()
        val useCase: StartDemo = mockk()
        val request = DemoStartRequest.stub(businessKind = DemoBusinessKind.FITNESS)
        coEvery { useCase.invoke(request, Language.EN) } returns Result.success(AuthTokens("access", "refresh"))
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { demo() })

        whenn()
        val response = createTestClient().post(AuthRouting.Api.Auth.Demo()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { useCase.invoke(request, Language.EN) }
    }

    @Test
    fun `should answer 429 DEMO_UNAVAILABLE once the demo cap is reached`() = routeTest {
        given()
        failingWith(StartDemo.Error.DemoUnavailable())

        whenn()
        val response = postStart()

        then()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(AuthErrorCodes.DEMO_UNAVAILABLE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer ACCOUNT_CREATION_FAILED when the account could not be stored`() = routeTest {
        given()
        failingWith(StartDemo.Error.AccountCreationFailed())

        whenn()
        val response = postStart()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.ACCOUNT_CREATION_FAILED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer LEGAL_DOCUMENTS_NOT_ACCEPTED when a document in effect is not accepted`() = routeTest {
        given()
        failingWith(CreateUser.Error.LegalDocumentsNotAccepted())

        whenn()
        val response = postStart()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENTS_NOT_ACCEPTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should answer LEGAL_DOCUMENT_VERSION_UNKNOWN for an unpublished version`() = routeTest {
        given()
        failingWith(LegalDocumentError.VersionUnknown())

        whenn()
        val response = postStart()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN, response.body<SimpleServerError>().errorCode)
    }

    private fun ApplicationTestBuilder.failingWith(error: Throwable) {
        val useCase: StartDemo = mockk()
        coEvery { useCase.invoke(any(), any()) } returns Result.failure(error)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { demo() })
    }

    private suspend fun ApplicationTestBuilder.postStart(): HttpResponse {
        return createTestClient().post(AuthRouting.Api.Auth.Demo()) { setBody(DemoStartRequest.stub()) }
    }
}
