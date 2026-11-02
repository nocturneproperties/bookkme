package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.delete_account.entity.VerifyDeleteAccRequest
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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class DeleteAccountTest {

    private suspend fun ApplicationTestBuilder.deleteAccountAnswering(userId: Uuid, outcome: Result<Unit>): HttpResponse {
        val useCase: DeleteAccount = mockk()
        coEvery { useCase.invoke(userId, any()) } returns outcome
        setupApplication(
            extension = {
                install(Authentication) {
                    provider { authenticate { context -> context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { deleteAccount() }
        )
        return createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(VerifyDeleteAccRequest("request-id", "{}")) }
    }

    @Test
    fun `should delete the caller's account`() = routeTest {
        given()
        val userId = Uuid.random()

        whenn()
        val response = deleteAccountAnswering(userId, Result.success(Unit))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should refuse a passkey that belongs to another account`() = routeTest {
        given()
        val userId = Uuid.random()

        whenn()
        val response = deleteAccountAnswering(userId, Result.failure(FinishAssertion.Error.PasskeyNotOwned()))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AuthErrorCodes.PASSKEY_NOT_OWNED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized without authentication`() = routeTest {
        given()
        val useCase: DeleteAccount = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { deleteAccount() }
        )

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account()) { setBody(VerifyDeleteAccRequest("request-id", "{}")) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
