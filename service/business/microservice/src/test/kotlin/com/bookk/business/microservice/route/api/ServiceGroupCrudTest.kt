package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.service.operation.CreateServiceGroup
import com.bookk.business.domain.api.service.operation.DeleteServiceGroup
import com.bookk.business.domain.api.service.operation.GetServiceGroups
import com.bookk.business.microservice.route.BusinessRouting
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ServiceGroupCrudTest {

    private val businessId = Uuid.random()
    private val userId = Uuid.random()

    private fun createTestServiceGroup() = ServiceGroup(
        id = Uuid.random(),
        businessId = businessId,
        name = "Test Group",
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun `should create service group`() = routeTest {
        given()
        val useCase: CreateServiceGroup = mockk()
        val group = createTestServiceGroup()

        coEvery { useCase.invoke(userId, group) } returns Result.success(group)

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
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.ServiceGroup(businessId = businessId)) {
            setBody(group)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity if group name exists`() = routeTest {
        given()
        val useCase: CreateServiceGroup = mockk()
        val group = createTestServiceGroup()

        coEvery { useCase.invoke(userId, group) } returns Result.failure(CreateServiceGroup.Error.ServiceGroupExist())

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
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.ServiceGroup(businessId = businessId)) {
            setBody(group)
        }

        then()
        val body = response.body<SimpleServerError>()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(200010, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity if validation error`() = routeTest {
        given()
        val useCase: CreateServiceGroup = mockk()
        val group = createTestServiceGroup()

        coEvery { useCase.invoke(userId, group) } returns Result.failure(CreateServiceGroup.Error.ValidationError())

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
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.ServiceGroup(businessId = businessId)) {
            setBody(group)
        }

        then()
        val body = response.body<SimpleServerError>()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(200011, body.errorCode)
    }

    @Test
    fun `should return service groups`() = routeTest {
        given()
        val useCase: GetServiceGroups = mockk()
        val groups = listOf(createTestServiceGroup())

        coEvery { useCase.invoke(businessId) } returns Result.success(groups)

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
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.ServiceGroup(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should delete service group`() = routeTest {
        given()
        val useCase: DeleteServiceGroup = mockk()
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
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.delete(BusinessRouting.Api.ServiceGroup.Id(BusinessRouting.Api.ServiceGroup(businessId = businessId), id))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return unauthorized when creating service group without authentication`() = routeTest {
        given()
        val useCase: CreateServiceGroup = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.ServiceGroup(businessId = businessId)) {
            setBody(createTestServiceGroup())
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when getting service groups without authentication`() = routeTest {
        given()
        val useCase: GetServiceGroups = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.ServiceGroup(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when deleting service group without authentication`() = routeTest {
        given()
        val useCase: DeleteServiceGroup = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceGroupCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.delete(BusinessRouting.Api.ServiceGroup.Id(BusinessRouting.Api.ServiceGroup(businessId = businessId), Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}