package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.DaySlots
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.TimeSlot
import com.bookk.appointments.domain.api.operation.GetAvailableSlots
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.error.BusinessErrorCodes
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAvailableSlotsTest {

    private val businessId = Uuid.random()
    private val employeeId = Uuid.random()
    private val services = listOf(RequestedService.stub(count = 2))
    private val from = LocalDate(2099, 6, 1)
    private val to = LocalDate(2099, 6, 2)
    private val request = AvailableSlotsRequest(businessId, employeeId, services, from, to)

    private val authenticated: Application.() -> Unit = {
        install(Authentication) {
            provider {
                authenticate { context ->
                    context.principal(AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random()))
                }
            }
        }
    }

    private suspend fun respondingWith(result: Result<List<DaySlots>>): GetAvailableSlots {
        val useCase: GetAvailableSlots = mockk()
        coEvery { useCase.invoke(businessId, employeeId, services, from, to) } returns result
        return useCase
    }

    @Test
    fun `should return the employee's free slots per date`() = routeTest {
        given()
        val start = Instant.parse("2099-06-01T09:00:00Z")
        val slots = listOf(
            DaySlots(from, listOf(TimeSlot.stub(start = start, end = Instant.parse("2099-06-01T10:00:00Z")))),
            DaySlots(to, emptyList()),
        )
        val useCase = respondingWith(Result.success(slots))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(slots, response.body<List<DaySlots>>())
    }

    @Test
    fun `should return unprocessable entity for an invalid service selection`() = routeTest {
        given()
        val useCase = respondingWith(Result.failure(GetAvailableSlots.Error.InvalidServiceSelection()))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.SERVICE_SELECTION_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity for an invalid date range`() = routeTest {
        given()
        val useCase = respondingWith(Result.failure(GetAvailableSlots.Error.InvalidRange()))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.SLOT_RANGE_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the employee is suspended`() = routeTest {
        given()
        val useCase = respondingWith(Result.failure(GetAppointmentRescheduleContext.Error.EmployeeSuspended()))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the business does not take appointments`() = routeTest {
        given()
        val useCase = respondingWith(Result.failure(Error.NotFound()))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized without a token`() = routeTest {
        given()
        val useCase: GetAvailableSlots = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return not found when the employee does not exist`() = routeTest {
        given()
        val useCase = respondingWith(Result.failure(GetAppointmentRescheduleContext.Error.EmployeeNotFound()))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_NOT_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when a service does not exist`() = routeTest {
        given()
        val useCase = respondingWith(Result.failure(GetAppointmentRescheduleContext.Error.ServiceNotFound()))
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.Slots()) { setBody(request) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND,
            response.body<SimpleServerError>().errorCode,
        )
    }
}
