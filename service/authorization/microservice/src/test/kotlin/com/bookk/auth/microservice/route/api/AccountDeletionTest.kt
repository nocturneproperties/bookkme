package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.delete_account.entity.AccountDeletionStatus
import com.bookk.auth.domain.api.delete_account.operation.CancelAccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.GetAccountDeletion
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AccountDeletionTest {

    private class Operations {
        val getAccountDeletion = mockk<GetAccountDeletion>()
        val cancelAccountDeletion = mockk<CancelAccountDeletion>()
    }

    private fun ApplicationTestBuilder.setup(operations: Operations, userId: Uuid?) {
        setupApplication(
            extension = {
                install(Authentication) {
                    if (userId == null) {
                        bearer { authenticate { null } }
                    } else {
                        provider {
                            authenticate { context ->
                                context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                            }
                        }
                    }
                }
            },
            diModule = module {
                single { operations.getAccountDeletion }
                single { operations.cancelAccountDeletion }
            },
            routeUnderTest = { accountDeletion() },
        )
    }

    @Test
    fun `should return the date of the caller's pending deletion`() = routeTest {
        given()
        val userId = Uuid.random()
        val operations = Operations()
        val status = AccountDeletionStatus(Instant.parse("2026-12-03T10:00:00Z"))
        coEvery { operations.getAccountDeletion(userId) } returns Result.success(status)
        setup(operations, userId)

        whenn()
        val response = createTestClient().get(AuthRouting.Api.Auth.Account.Deletion())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(status, response.body<AccountDeletionStatus>())
    }

    @Test
    fun `should return no date when the caller has no pending deletion`() = routeTest {
        given()
        val userId = Uuid.random()
        val operations = Operations()
        coEvery { operations.getAccountDeletion(userId) } returns Result.success(AccountDeletionStatus(null))
        setup(operations, userId)

        whenn()
        val response = createTestClient().get(AuthRouting.Api.Auth.Account.Deletion())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AccountDeletionStatus(null), response.body<AccountDeletionStatus>())
    }

    @Test
    fun `should cancel the caller's pending deletion`() = routeTest {
        given()
        val userId = Uuid.random()
        val operations = Operations()
        coEvery { operations.cancelAccountDeletion(userId) } returns Result.success(Unit)
        setup(operations, userId)

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account.Deletion())

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { operations.cancelAccountDeletion(userId) }
    }

    @Test
    fun `should return a server error when the cancellation fails`() = routeTest {
        given()
        val userId = Uuid.random()
        val operations = Operations()
        coEvery { operations.cancelAccountDeletion(userId) } returns Result.failure(IllegalStateException("down"))
        setup(operations, userId)

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account.Deletion())

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `should not read the deletion status without authentication`() = routeTest {
        given()
        val operations = Operations()
        setup(operations, userId = null)

        whenn()
        val response = createTestClient().get(AuthRouting.Api.Auth.Account.Deletion())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { operations.getAccountDeletion(any()) }
    }

    @Test
    fun `should not cancel a deletion without authentication`() = routeTest {
        given()
        val operations = Operations()
        setup(operations, userId = null)

        whenn()
        val response = createTestClient().delete(AuthRouting.Api.Auth.Account.Deletion())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { operations.cancelAccountDeletion(any()) }
    }
}
