package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetBusinessDetailsTest {

    private val businessId = Uuid.random()

    @Test
    fun `should return the business details`() = routeTest {
        given()
        val useCase: GetBusinessDetails = mockk()
        val business = Business.stub(id = businessId)
        coEvery { useCase.invoke(businessId) } returns Result.success(business)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getBusinessDetails() }
        )

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Internal.Business.Id(id = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(business, response.body<Business>())
    }

    @Test
    fun `should return not found when business does not exist`() = routeTest {
        given()
        val useCase: GetBusinessDetails = mockk()
        coEvery { useCase.invoke(businessId) } returns Result.failure(GetBusinessDetails.Error.NotFound())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getBusinessDetails() }
        )

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Internal.Business.Id(id = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }
}
