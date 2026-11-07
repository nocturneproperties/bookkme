package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.delete_account.entity.DeleteAccountRequest
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

internal class DeleteAccountTest {

    private fun ApplicationTestBuilder.setupDeletion(userId: Uuid, outcome: Result<Unit>): DeleteAccount {
        val useCase: DeleteAccount = mockk()
        coEvery { useCase.invoke(userId, any()) } returns outcome
        setupApplication(
            extension = {
                install(Authentication) {
                    provider { authenticate { context -> context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { deleteAccount() },
        )
        return useCase
    }

    @Test
    fun `should delete the caller's account with the supplied action confirmation token`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase = setupDeletion(userId, Result.success(Unit))

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(DeleteAccountRequest("action-confirmation-token")) }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase(userId, "action-confirmation-token") }
    }

    @Test
    fun `should return forbidden when the action confirmation token is invalid`() = routeTest {
        given()
        val userId = Uuid.random()
        setupDeletion(userId, Result.failure(DeleteAccount.Error.ActionConfirmationInvalid()))

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(DeleteAccountRequest("invalid")) }

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AuthErrorCodes.ACTION_CONFIRMATION_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the account is missing`() = routeTest {
        given()
        val userId = Uuid.random()
        setupDeletion(userId, Result.failure(DeleteAccount.Error.InvalidCredentials()))

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(DeleteAccountRequest("action-confirmation-token")) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.VERIFICATION_FAILED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should pass an empty token for a request without a body`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase = setupDeletion(userId, Result.failure(DeleteAccount.Error.ActionConfirmationInvalid()))

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account())

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AuthErrorCodes.ACTION_CONFIRMATION_INVALID, response.body<SimpleServerError>().errorCode)
        coVerify(exactly = 1) { useCase(userId, "") }
    }

    @Test
    fun `should pass an empty token when the token field is omitted`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase = setupDeletion(userId, Result.failure(DeleteAccount.Error.ActionConfirmationInvalid()))

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(DeleteAccountRequest()) }

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AuthErrorCodes.ACTION_CONFIRMATION_INVALID, response.body<SimpleServerError>().errorCode)
        coVerify(exactly = 1) { useCase(userId, "") }
    }

    @Test
    fun `should not interpret the old passkey assertion body as an action confirmation token`() = routeTest {
        given()
        val userId = Uuid.random()
        val useCase = setupDeletion(userId, Result.failure(DeleteAccount.Error.ActionConfirmationInvalid()))
        val oldBody = byteArrayOf(10, 10) + "request-id".encodeToByteArray() + byteArrayOf(18, 2) + "{}".encodeToByteArray()

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) {
            contentType(ContentType.Application.ProtoBuf)
            setBody(oldBody)
        }

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AuthErrorCodes.ACTION_CONFIRMATION_INVALID, response.body<SimpleServerError>().errorCode)
        coVerify(exactly = 1) { useCase(userId, "") }
    }

    @Test
    fun `should return unauthorized without authentication`() = routeTest {
        given()
        val useCase: DeleteAccount = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { deleteAccount() },
        )

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(DeleteAccountRequest("action-confirmation-token")) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }
}
