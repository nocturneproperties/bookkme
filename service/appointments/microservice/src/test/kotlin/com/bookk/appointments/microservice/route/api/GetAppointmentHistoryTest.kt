package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentPage
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.HistoryDates
import com.bookk.appointments.domain.api.entity.HistoryFilterError
import com.bookk.appointments.domain.api.entity.HistoryOrder
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.domain.entity.CoreErrorCodes
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.PageError
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import library.permissions.EmployeeAccessSuspended
import library.permissions.PermissionErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetAppointmentHistoryTest {

    private fun ApplicationTestBuilder.serve(useCase: GetAppointmentHistory, userId: Uuid?) {
        setupApplication(
            extension = {
                install(Authentication) {
                    if (userId == null) {
                        bearer { authenticate { null } }
                    } else {
                        provider {
                            authenticate { context ->
                                context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                            }
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() },
        )
    }

    @Test
    fun `should return a page of the history including unresolved appointments`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val appointments = listOf(
            Appointment.stub(businessId = businessId).copy(status = AppointmentStatus.UNRESOLVED),
        )
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), 10, "next") } returns
            Result.success(AppointmentPage(appointments, nextCursor = "after"))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.AppointmentHistory(businessId = businessId, limit = 10, cursor = "next"),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<AppointmentPage>()
        assertEquals(appointments, page.data)
        assertEquals("after", page.nextCursor)
    }

    @Test
    fun `should read the first page when no limit or cursor is given`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), null, null) } returns
            Result.success(AppointmentPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.AppointmentHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(null, response.body<AppointmentPage>().nextCursor)
    }

    @Test
    fun `should return unprocessable entity for an invalid limit`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), 0, null) } returns
            Result.failure(PageError.LimitInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.AppointmentHistory(businessId = businessId, limit = 0))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_LIMIT_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity for an invalid cursor`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), null, "bad") } returns
            Result.failure(PageError.CursorInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.AppointmentHistory(businessId = businessId, cursor = "bad"),
        )

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_CURSOR_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the caller has no view grant on the history`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), null, null) } returns
            Result.failure(Error.OperationNotAllowed())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.AppointmentHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), null, null) } returns
            Result.failure(EmployeeAccessSuspended())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.AppointmentHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when getting appointment history without authentication`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        serve(useCase, userId = null)

        whenn()
        val response = createTestClient().get(Api.AppointmentHistory(businessId = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should pass the query filter and the cursor to the history`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(query = "John"), null, "next") } returns
            Result.success(AppointmentPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.AppointmentHistory(businessId = businessId, cursor = "next", query = "John"),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return server error when getting appointment history fails`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentHistoryFilter(), null, null) } returns
            Result.failure(Exception("Database error"))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.AppointmentHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `should pass every filter from the query string to the history`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val filter = AppointmentHistoryFilter(
            clientId = Uuid.random(),
            employeeId = Uuid.random(),
            statuses = setOf(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW),
            dates = HistoryDates(from = LocalDate(2027, 3, 1), to = LocalDate(2027, 3, 31)),
            order = HistoryOrder.SOONEST_FIRST,
        )
        coEvery { useCase.invoke(userId, businessId, filter, null, null) } returns
            Result.success(AppointmentPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.AppointmentHistory(
                businessId = businessId,
                clientId = filter.clientId,
                employeeId = filter.employeeId,
                status = listOf(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW),
                from = LocalDate(2027, 3, 1),
                to = LocalDate(2027, 3, 31),
                order = HistoryOrder.SOONEST_FIRST,
            ),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity for history dates that end before they start`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val filter = AppointmentHistoryFilter(
            dates = HistoryDates(from = LocalDate(2027, 3, 2), to = LocalDate(2027, 3, 1)),
        )
        coEvery { useCase.invoke(userId, businessId, filter, null, null) } returns
            Result.failure(HistoryFilterError.DateRangeInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.AppointmentHistory(businessId = businessId, from = LocalDate(2027, 3, 2), to = LocalDate(2027, 3, 1)),
        )

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.HISTORY_DATE_RANGE_INVALID, response.body<SimpleServerError>().errorCode)
    }
}
