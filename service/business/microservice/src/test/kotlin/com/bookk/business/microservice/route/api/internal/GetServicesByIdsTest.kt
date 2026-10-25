package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServicesByIdsRequest
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetServicesByIdsTest {

    private val businessId = Uuid.random()

    private fun resource() = BusinessRouting.Api.Internal.Business.Id.Services(
        parent = BusinessRouting.Api.Internal.Business.Id(id = businessId)
    )

    @Test
    fun `should return the resolved services`() = routeTest {
        given()
        val useCase: GetServicesByIds = mockk()
        val services = listOf(Service.stub(businessId = businessId), Service.stub(businessId = businessId))
        val body = ServicesByIdsRequest.stub(serviceIds = services.map { it.id })
        coEvery { useCase.invoke(businessId, body.serviceIds) } returns Result.success(services)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getServicesByIds() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(services, response.body<List<Service>>())
    }

    @Test
    fun `should return unprocessable entity when a service is missing`() = routeTest {
        given()
        val useCase: GetServicesByIds = mockk()
        val body = ServicesByIdsRequest.stub()
        coEvery { useCase.invoke(businessId, body.serviceIds) } returns Result.failure(GetServicesByIds.Error.ServiceNotFound())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getServicesByIds() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }
}
