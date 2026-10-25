package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.service.operation.CreateService
import com.bookk.business.domain.api.service.operation.DeleteService
import com.bookk.business.domain.api.service.operation.GetServices
import com.bookk.business.domain.api.service.operation.UpdateService
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.CoreErrorCodes
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
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.sql.SQLException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ServiceCrudTest {

    private val businessId = Uuid.random()
    private val userId = Uuid.random()

    private fun createTestServiceGroup() = ServiceGroup(
        id = Uuid.random(),
        businessId = businessId,
        name = "Test Group",
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun createTestService(group: ServiceGroup = createTestServiceGroup()) = Service(
        id = Uuid.random(),
        businessId = businessId,
        group = group,
        name = "Test Service",
        duration = 30.minutes,
        price = Money.parse("USD 100"),
        isAvailable = true,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun `should create service`() = routeTest {
        given()
        val useCase: CreateService = mockk()
        val service = createTestService()
        
        coEvery { useCase.invoke(userId, service) } returns Result.success(service)
        
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
            routeUnderTest = { serviceCrud() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(service)
        }
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should update service`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = createTestService()
        
        coEvery { useCase.invoke(userId, service) } returns Result.success(service)
        
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
            routeUnderTest = { serviceCrud() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return services`() = routeTest {
        given()
        val useCase: GetServices = mockk()
        val services = listOf(createTestService())
        
        coEvery { useCase.invoke(businessId) } returns Result.success(services)
        
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
            routeUnderTest = { serviceCrud() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Service(businessId = businessId))
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should delete service`() = routeTest {
        given()
        val useCase: DeleteService = mockk()
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
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.delete(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), id))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return unprocessable entity when service already exists on create`() = routeTest {
        given()
        val useCase: CreateService = mockk()
        val service = createTestService()
        coEvery { useCase.invoke(userId, service) } returns Result.failure(CreateService.Error.ServiceExist())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when service validation error on create`() = routeTest {
        given()
        val useCase: CreateService = mockk()
        val service = createTestService()
        coEvery { useCase.invoke(userId, service) } returns Result.failure(CreateService.Error.ValidationError())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_NAME_VALIDATION_ERROR, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when service already exists on update`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = createTestService()
        coEvery { useCase.invoke(userId, service) } returns Result.failure(UpdateService.Error.ServiceExist())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when service validation error on update`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = createTestService()
        coEvery { useCase.invoke(userId, service) } returns Result.failure(CreateService.Error.ValidationError())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_NAME_VALIDATION_ERROR, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when creating service without authentication`() = routeTest {
        given()
        val useCase: CreateService = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(createTestService())
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when updating service without authentication`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = createTestService()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when getting services without authentication`() = routeTest {
        given()
        val useCase: GetServices = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Service(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when deleting service without authentication`() = routeTest {
        given()
        val useCase: DeleteService = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.delete(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unprocessable entity when price precision is invalid on create`() = routeTest {
        given()
        val useCase: CreateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(CreateService.Error.PricePrecisionInvalid())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_PRICE_PRECISION_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when price precision is invalid on update`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(UpdateService.Error.PricePrecisionInvalid())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_PRICE_PRECISION_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the business currency is invalid on create`() = routeTest {
        given()
        val useCase: CreateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(CreateService.Error.BusinessCurrencyInvalid())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_BUSINESS_CURRENCY_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the business currency is invalid on update`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(UpdateService.Error.BusinessCurrencyInvalid())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_BUSINESS_CURRENCY_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return bad request when path service id does not match body id on update`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = Service.stub(businessId = businessId)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), Uuid.random())) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }

    @Test
    fun `should return unprocessable entity when the service group is not in the business on create`() = routeTest {
        given()
        val useCase: CreateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(CreateService.Error.ServiceGroupNotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Service(businessId = businessId)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_GROUP_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the service group is not in the business on update`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(UpdateService.Error.ServiceGroupNotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_SERVICE_GROUP_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return conflict when the update keeps conflicting with concurrent writes`() = routeTest {
        given()
        val useCase: UpdateService = mockk()
        val service = Service.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, service) } returns Result.failure(Error.TransactionConflict("conflict", SQLException()))

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { serviceCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Service.Id(BusinessRouting.Api.Service(businessId = businessId), service.id)) {
            setBody(service)
        }

        then()
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(CoreErrorCodes.TRANSACTION_CONFLICT, response.body<SimpleServerError>().errorCode)
    }
}
