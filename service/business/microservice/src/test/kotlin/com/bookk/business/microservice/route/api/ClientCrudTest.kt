package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.entity.toDomain
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.api.client.operation.GetClients
import com.bookk.business.domain.api.client.operation.UpdateClient
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
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.patch
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ClientCrudTest {

    private val businessId = Uuid.random()
    private val userId = Uuid.random()

    @Test
    fun `should create client`() = routeTest {
        given()
        val useCase: CreateClient = mockk()
        val clientRemote = ClientRemote.stub()
        val client = clientRemote.toDomain()
        
        coEvery { useCase.invoke(userId, businessId, client) } returns Result.success(clientRemote)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { clientCrud() }
        )
        
        whenn()
        val httpClient = createTestClient()
        val response = httpClient.post(BusinessRouting.Api.Clients(businessId = businessId)) {
            setBody(clientRemote)
        }
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return clients`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        val clients = listOf(ClientRemote.stub())
        
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(clients)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { clientCrud() }
        )
        
        whenn()
        val httpClient = createTestClient()
        val response = httpClient.get(BusinessRouting.Api.Clients(businessId = businessId))
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should delete client`() = routeTest {
        given()
        val useCase: DeleteClient = mockk()
        val id = Uuid.random()

        coEvery { useCase.invoke(userId, businessId, id) } returns Result.success(Unit)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.delete(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private fun updateModel(
        id: Uuid,
        name: String? = null,
        lastName: String? = null,
        phone: String? = null,
        email: String? = null,
        description: String? = null
    ) = ClientUpdateModel(id = id, name = name, lastName = lastName, phone = phone, email = email, description = description)

    @Test
    fun `should update client`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = id, description = "Prefers evening appointments")
        val updated = ClientRemote.stub()

        coEvery { useCase.invoke(userId, businessId, body) } returns Result.success(updated)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updated.id, response.body<ClientRemote>().id)
    }

    @Test
    fun `should return bad request when path id does not match body id`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = Uuid.random(), description = "notes")

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return not found when updating a client that does not exist`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = id, description = "notes")
        coEvery { useCase.invoke(userId, businessId, body) } returns Result.failure(UpdateClient.Error.NotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when updating personal info of an integrated client`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = id, name = "New Name")
        coEvery { useCase.invoke(userId, businessId, body) } returns Result.failure(UpdateClient.Error.PersonalInfoNotEditable())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_PERSONAL_INFO_NOT_EDITABLE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when updated client phone already exists`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = id, phone = "999999999")
        coEvery { useCase.invoke(userId, businessId, body) } returns Result.failure(UpdateClient.Error.ClientExist())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when updated client validation fails`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = id, phone = "not-a-phone")
        coEvery { useCase.invoke(userId, businessId, body) } returns Result.failure(UpdateClient.Error.ClientValidationError())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when user is not allowed to update client`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()
        val body = updateModel(id = id, description = "notes")
        coEvery { useCase.invoke(userId, businessId, body) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(body)
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when updating client without authentication`() = routeTest {
        given()
        val useCase: UpdateClient = mockk()
        val id = Uuid.random()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.patch(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id)) {
            setBody(updateModel(id = id, description = "notes"))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unprocessable entity when client already exists`() = routeTest {
        given()
        val useCase: CreateClient = mockk()
        val clientRemote = ClientRemote.stub()
        coEvery { useCase.invoke(userId, businessId, clientRemote.toDomain()) } returns Result.failure(CreateClient.Error.ClientExist())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.post(BusinessRouting.Api.Clients(businessId = businessId)) {
            setBody(clientRemote)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when client validation error`() = routeTest {
        given()
        val useCase: CreateClient = mockk()
        val clientRemote = ClientRemote.stub()
        coEvery { useCase.invoke(userId, businessId, clientRemote.toDomain()) } returns Result.failure(CreateClient.Error.ClientValidationError())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.post(BusinessRouting.Api.Clients(businessId = businessId)) {
            setBody(clientRemote)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when client has no phone or email`() = routeTest {
        given()
        val useCase: CreateClient = mockk()
        val clientRemote = ClientRemote.stub()
        coEvery { useCase.invoke(userId, businessId, clientRemote.toDomain()) } returns Result.failure(CreateClient.Error.MissingContactInfo())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.post(BusinessRouting.Api.Clients(businessId = businessId)) {
            setBody(clientRemote)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_MISSING_CONTACT_INFO, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when deleting client that does not exist`() = routeTest {
        given()
        val useCase: DeleteClient = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id) } returns Result.failure(DeleteClient.Error.NotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.delete(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), id))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when user is not allowed to get clients`() = routeTest {
        given()
        val useCase: GetClients = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Clients(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return not found when user is not allowed to create client`() = routeTest {
        given()
        val useCase: CreateClient = mockk()
        val clientRemote = ClientRemote.stub()
        coEvery {
            useCase.invoke(userId, businessId, clientRemote.toDomain())
        } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Clients(businessId = businessId)) {
            setBody(clientRemote)
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return not found when user is not allowed to delete client`() = routeTest {
        given()
        val useCase: DeleteClient = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.delete(
            BusinessRouting.Api.Clients.Id(parent = BusinessRouting.Api.Clients(businessId = businessId), id = id)
        )

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when creating client without authentication`() = routeTest {
        given()
        val useCase: CreateClient = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.post(BusinessRouting.Api.Clients(businessId = businessId)) {
            setBody(ClientRemote.stub())
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when getting clients without authentication`() = routeTest {
        given()
        val useCase: GetClients = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.get(BusinessRouting.Api.Clients(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when deleting client without authentication`() = routeTest {
        given()
        val useCase: DeleteClient = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientCrud() }
        )

        whenn()
        val httpClient = createTestClient()
        val response = httpClient.delete(BusinessRouting.Api.Clients.Id(BusinessRouting.Api.Clients(businessId = businessId), Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
