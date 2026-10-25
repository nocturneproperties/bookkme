package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContextRequest
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
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
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetAppointmentBookingContextTest {

    private val businessId = Uuid.random()

    private fun resource() = BusinessRouting.Api.Internal.Business.Id.AppointmentBookingContext(
        parent = BusinessRouting.Api.Internal.Business.Id(id = businessId)
    )

    @Test
    fun `should return the resolved booking context`() = routeTest {
        given()
        val useCase: GetAppointmentBookingContext = mockk()
        val body = AppointmentBookingContextRequest.stub()
        val context = AppointmentBookingContext.stub()
        coEvery {
            useCase.invoke(businessId, body.employeeId, body.userId, body.serviceIds)
        } returns Result.success(context)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentBookingContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val received = response.body<AppointmentBookingContext>()
        assertEquals(context.employee, received.employee)
        assertEquals(context.services, received.services)
        assertEquals(context.client.id, received.client.id)
        assertEquals(context.client.name, received.client.name)
        assertEquals(context.client.lastName, received.client.lastName)
        assertEquals(context.client.phone, received.client.phone)
        assertEquals(context.client.email, received.client.email)
    }

    @Test
    fun `should return unprocessable entity when employee is suspended`() = routeTest {
        given()
        val useCase: GetAppointmentBookingContext = mockk()
        val body = AppointmentBookingContextRequest.stub()
        coEvery {
            useCase.invoke(businessId, body.employeeId, body.userId, body.serviceIds)
        } returns Result.failure(GetAppointmentBookingContext.Error.EmployeeSuspended())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentBookingContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when employee is missing`() = routeTest {
        given()
        val useCase: GetAppointmentBookingContext = mockk()
        val body = AppointmentBookingContextRequest.stub()
        coEvery {
            useCase.invoke(businessId, body.employeeId, body.userId, body.serviceIds)
        } returns Result.failure(GetAppointmentBookingContext.Error.EmployeeNotFound())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentBookingContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unprocessable entity when services are missing`() = routeTest {
        given()
        val useCase: GetAppointmentBookingContext = mockk()
        val body = AppointmentBookingContextRequest.stub()
        coEvery {
            useCase.invoke(businessId, body.employeeId, body.userId, body.serviceIds)
        } returns Result.failure(GetAppointmentBookingContext.Error.ServiceNotFound())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentBookingContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `should return unprocessable entity when service list is empty`() = routeTest {
        given()
        val useCase: GetAppointmentBookingContext = mockk()
        val body = AppointmentBookingContextRequest.stub()
        coEvery {
            useCase.invoke(businessId, body.employeeId, body.userId, body.serviceIds)
        } returns Result.failure(GetAppointmentBookingContext.Error.EmptyServiceList())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getAppointmentBookingContext() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(resource()) { setBody(body) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }
}
