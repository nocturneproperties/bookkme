package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContextRequest
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
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
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetAppointmentRescheduleContextTest {

    private val businessId = Uuid.random()

    private fun resource() = BusinessRouting.Api.Internal.Business.Id.AppointmentRescheduleContext(
        parent = BusinessRouting.Api.Internal.Business.Id(id = businessId)
    )

    @Test
    fun `should return the resolved reschedule context`() = routeTest {
        given()
        val useCase: GetAppointmentRescheduleContext = mockk()
        val body = AppointmentRescheduleContextRequest.stub()
        val context = AppointmentRescheduleContext.stub()
        coEvery { useCase.invoke(businessId, body.employeeId, body.serviceIds) } returns Result.success(context)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentRescheduleContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val received = response.body<AppointmentRescheduleContext>()
        assertEquals(context.employee, received.employee)
        assertEquals(context.services, received.services)
    }

    @Test
    fun `should resolve services only when no employee is requested`() = routeTest {
        given()
        val useCase: GetAppointmentRescheduleContext = mockk()
        val body = AppointmentRescheduleContextRequest.stub(employeeId = null)
        val context = AppointmentRescheduleContext.stub(employee = null)
        coEvery { useCase.invoke(businessId, null, body.serviceIds) } returns Result.success(context)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentRescheduleContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(null, response.body<AppointmentRescheduleContext>().employee)
    }

    @Test
    fun `should return not found when the employee is missing`() = routeTest {
        given()
        assertRejected(GetAppointmentRescheduleContext.Error.EmployeeNotFound(), HttpStatusCode.NotFound, BusinessErrorCodes.BUSINESS_EMPLOYEE_NOT_EXISTS)
    }

    @Test
    fun `should return unprocessable entity when the employee is suspended`() = routeTest {
        given()
        assertRejected(GetAppointmentRescheduleContext.Error.EmployeeSuspended(), HttpStatusCode.UnprocessableEntity, BusinessErrorCodes.BUSINESS_EMPLOYEE_SUSPENDED)
    }

    @Test
    fun `should return unprocessable entity when a service is missing`() = routeTest {
        given()
        assertRejected(GetAppointmentRescheduleContext.Error.ServiceNotFound(), HttpStatusCode.UnprocessableEntity, BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND)
    }

    private suspend fun ApplicationTestBuilder.assertRejected(error: Throwable, status: HttpStatusCode, code: Int) {
        val useCase: GetAppointmentRescheduleContext = mockk()
        val body = AppointmentRescheduleContextRequest.stub()
        coEvery { useCase.invoke(businessId, body.employeeId, body.serviceIds) } returns Result.failure(error)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentRescheduleContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(status, response.status)
        assertEquals(code, response.body<SimpleServerError>().errorCode)
    }
}
