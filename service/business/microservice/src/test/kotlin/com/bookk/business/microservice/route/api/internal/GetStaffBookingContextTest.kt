package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.entity.StaffBookingContextRequest
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetStaffBookingContextTest {

    private val businessId = Uuid.random()
    private val request = StaffBookingContextRequest.stub()

    private fun ApplicationTestBuilder.staffBookingContextRoute(result: Result<AppointmentBookingContext>): HttpClient {
        val useCase: GetStaffBookingContext = mockk()
        coEvery { useCase.invoke(businessId, request.employeeId, request.clientId, request.serviceIds) } returns result
        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getStaffBookingContext() },
        )
        return createTestClient()
    }

    private suspend fun HttpClient.postBody(): HttpResponse = post(
        BusinessRouting.Api.Internal.Business.Id.StaffBookingContext(
            parent = BusinessRouting.Api.Internal.Business.Id(id = businessId),
        ),
    ) {
        setBody(request)
    }

    @Test
    fun `should return the resolved booking context`() = routeTest {
        given()
        val context = AppointmentBookingContext.stub()
        val client = staffBookingContextRoute(Result.success(context))

        whenn()
        val response = client.postBody()

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val received = response.body<AppointmentBookingContext>()
        assertEquals(context.employee, received.employee)
        assertEquals(context.client.id, received.client.id)
        assertEquals(context.services, received.services)
    }

    @Test
    fun `should return not found when the client is not a client of the business`() = routeTest {
        given()
        val client = staffBookingContextRoute(Result.failure(GetStaffBookingContext.Error.ClientNotFound()))

        whenn()
        val response = client.postBody()

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the employee is not in the business`() = routeTest {
        given()
        val client = staffBookingContextRoute(Result.failure(GetStaffBookingContext.Error.EmployeeNotFound()))

        whenn()
        val response = client.postBody()

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the employee is suspended`() = routeTest {
        given()
        val client = staffBookingContextRoute(Result.failure(GetStaffBookingContext.Error.EmployeeSuspended()))

        whenn()
        val response = client.postBody()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when a service is missing`() = routeTest {
        given()
        val client = staffBookingContextRoute(Result.failure(GetStaffBookingContext.Error.ServiceNotFound()))

        whenn()
        val response = client.postBody()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when no services are requested`() = routeTest {
        given()
        val client = staffBookingContextRoute(Result.failure(GetStaffBookingContext.Error.EmptyServiceList()))

        whenn()
        val response = client.postBody()

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_QUOTE_EMPTY_SERVICE_LIST, response.body<SimpleServerError>().errorCode)
    }
}
