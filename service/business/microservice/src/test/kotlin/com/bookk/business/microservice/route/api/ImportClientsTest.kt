package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.client.entity.ClientImportResult
import com.bookk.business.domain.api.client.entity.RejectedClient
import com.bookk.business.domain.api.client.operation.ImportClients
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.Error
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
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ImportClientsTest {

    private val businessId = Uuid.random()
    private val userId = Uuid.random()

    private val request = ClientImportRequest(
        clients = listOf(
            ClientImportEntry(name = "Ann", lastName = "Lee", phone = "+1111", email = null, description = "Regular"),
            ClientImportEntry(name = "Ben", lastName = "Roe", phone = null, email = "ben@client.com", description = null)
        )
    )

    private fun authenticated(): Application.() -> Unit = {
        install(Authentication) {
            provider {
                authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
            }
        }
    }

    private fun ApplicationTestBuilder.setup(useCase: ImportClients, extension: Application.() -> Unit = authenticated()) {
        setupApplication(
            extension = extension,
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )
    }

    @Test
    fun `should import clients and return created and rejected ones`() = routeTest {
        given()
        val useCase: ImportClients = mockk()
        val imported = ClientImportResult.stub(rejected = listOf(RejectedClient(1, BusinessErrorCodes.BUSINESS_CLIENT_EXISTS)))
        coEvery {
            useCase.invoke(
                userId,
                businessId,
                match { clients ->
                    clients.map { it.name } == listOf("Ann", "Ben") &&
                        clients[0].description == "Regular" &&
                        clients[1].email == "ben@client.com"
                }
            )
        } returns Result.success(imported)
        setup(useCase)

        whenn()
        val response = createTestClient().post(BusinessRouting.Api.Clients.Import(BusinessRouting.Api.Clients(businessId = businessId))) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ClientImportResult>()
        assertEquals(imported.created.map { it.id }, body.created.map { it.id })
        assertEquals(imported.rejected, body.rejected)
    }

    @Test
    fun `should return unprocessable entity when the import is too large`() = routeTest {
        given()
        val useCase: ImportClients = mockk()
        coEvery { useCase.invoke(userId, businessId, any()) } returns Result.failure(ImportClients.Error.TooManyClients())
        setup(useCase)

        whenn()
        val response = createTestClient().post(BusinessRouting.Api.Clients.Import(BusinessRouting.Api.Clients(businessId = businessId))) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_IMPORT_TOO_LARGE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when a concurrent write takes a phone or email first`() = routeTest {
        given()
        val useCase: ImportClients = mockk()
        coEvery { useCase.invoke(userId, businessId, any()) } returns Result.failure(ImportClients.Error.ClientExist())
        setup(useCase)

        whenn()
        val response = createTestClient().post(BusinessRouting.Api.Clients.Import(BusinessRouting.Api.Clients(businessId = businessId))) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when user is not allowed to import clients`() = routeTest {
        given()
        val useCase: ImportClients = mockk()
        coEvery { useCase.invoke(userId, businessId, any()) } returns Result.failure(Error.OperationNotAllowed())
        setup(useCase)

        whenn()
        val response = createTestClient().post(BusinessRouting.Api.Clients.Import(BusinessRouting.Api.Clients(businessId = businessId))) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden when the caller is a suspended employee`() = routeTest {
        given()
        val useCase: ImportClients = mockk()
        coEvery { useCase.invoke(userId, businessId, any()) } returns Result.failure(EmployeeAccessSuspended())
        setup(useCase)

        whenn()
        val response = createTestClient().post(BusinessRouting.Api.Clients.Import(BusinessRouting.Api.Clients(businessId = businessId))) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when importing clients without authentication`() = routeTest {
        given()
        val useCase: ImportClients = mockk()
        setup(useCase, extension = { install(Authentication) { bearer { authenticate { null } } } })

        whenn()
        val response = createTestClient().post(BusinessRouting.Api.Clients.Import(BusinessRouting.Api.Clients(businessId = businessId))) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
