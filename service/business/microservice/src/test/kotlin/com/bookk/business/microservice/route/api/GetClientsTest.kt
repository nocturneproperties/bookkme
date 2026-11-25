package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.client.entity.ClientPage
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.operation.GetClients
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.domain.entity.CoreErrorCodes
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.PageError
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.PermissionErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetClientsTest {

    private fun ApplicationTestBuilder.serve(useCase: GetClients, userId: Uuid?) {
        setupApplication(
            extension = {
                install(Authentication) {
                    if (userId == null) {
                        bearer { authenticate { null } }
                    } else {
                        provider {
                            authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() },
        )
    }

    @Test
    fun `should return a page of clients for the limit, cursor and query`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val clients = listOf(ClientRemote.stub())
        coEvery { useCase.invoke(userId, businessId, 20, "next", "alice smith") } returns
            Result.success(ClientPage(clients, nextCursor = "after"))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.ClientList(businessId = businessId, limit = 20, cursor = "next", query = "alice smith"),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<ClientPage>()
        assertEquals(clients.map { it.id }, page.data.map { it.id })
        assertEquals("after", page.nextCursor)
    }

    @Test
    fun `should read the first page of every client when nothing is given`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, null, null, null) } returns
            Result.success(ClientPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.ClientList(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.body<ClientPage>().nextCursor)
    }

    @Test
    fun `should return unprocessable entity for an invalid limit`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, 500, null, null) } returns
            Result.failure(PageError.LimitInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.ClientList(businessId = businessId, limit = 500))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_LIMIT_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity for an invalid cursor`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, null, "bad", null) } returns
            Result.failure(PageError.CursorInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.ClientList(businessId = businessId, cursor = "bad"))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_CURSOR_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when user is not allowed to get clients`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, null, null, null) } returns
            Result.failure(Error.OperationNotAllowed())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.ClientList(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, null, null, null) } returns
            Result.failure(EmployeeAccessSuspended())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.ClientList(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when getting clients without authentication`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        serve(useCase, userId = null)

        whenn()
        val response = createTestClient().get(Api.ClientList(businessId = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
