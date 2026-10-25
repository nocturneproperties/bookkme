package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
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
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetBusinessPermissionTest {

    private val userId = Uuid.random()
    private val businessId = Uuid.random()

    private fun permissionsResource(resource: BusinessResource = BusinessResource.BUSINESS) = BusinessRouting.Api.Internal.Business.Id.Permissions(
        parent = BusinessRouting.Api.Internal.Business.Id(id = businessId),
        userId = userId,
        resource = resource
    )

    @Test
    fun `should return the permission the user holds on the requested resource`() = routeTest {
        given()
        val useCase: GetBusinessPermission = mockk()
        coEvery { useCase.invoke(userId, businessId, BusinessResource.BUSINESS) } returns Result.success(ResourcePermission.FULL)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getBusinessPermission() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(permissionsResource())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ResourcePermission.FULL, response.body<ResourcePermission>())
    }

    @Test
    fun `should return no permission when the user holds none`() = routeTest {
        given()
        val useCase: GetBusinessPermission = mockk()
        coEvery { useCase.invoke(userId, businessId, BusinessResource.EMPLOYEES) } returns Result.success(ResourcePermission.NONE)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getBusinessPermission() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(permissionsResource(BusinessResource.EMPLOYEES))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ResourcePermission.NONE, response.body<ResourcePermission>())
    }

    @Test
    fun `should return not found when the business is unknown`() = routeTest {
        given()
        val useCase: GetBusinessPermission = mockk()
        coEvery { useCase.invoke(userId, businessId, BusinessResource.BUSINESS) } returns Result.failure(Error.NotFound())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getBusinessPermission() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(permissionsResource())

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the user is a suspended employee`() = routeTest {
        given()
        val useCase: GetBusinessPermission = mockk()
        coEvery { useCase.invoke(userId, businessId, BusinessResource.BUSINESS) } returns
            Result.failure(EmployeeAccessSuspended())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getBusinessPermission() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(permissionsResource())

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }
}
