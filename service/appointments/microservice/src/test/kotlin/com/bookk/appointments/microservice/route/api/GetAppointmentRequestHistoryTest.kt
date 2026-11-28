package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentRequestPage
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.HistoryDates
import com.bookk.appointments.domain.api.entity.HistoryFilterError
import com.bookk.appointments.domain.api.entity.HistoryOrder
import com.bookk.appointments.domain.api.operation.GetAppointmentRequestHistory
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

internal class GetAppointmentRequestHistoryTest {

    private fun ApplicationTestBuilder.serve(useCase: GetAppointmentRequestHistory, userId: Uuid?) {
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
            routeUnderTest = { requests() },
        )
    }

    @Test
    fun `should return a page of the request history`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val requests = listOf(
            AppointmentRequest.stub(businessId = businessId, employee = EmployeeSnapshot.stub(userId = userId)),
        )
        coEvery { useCase.invoke(userId, businessId, AppointmentRequestHistoryFilter(), 10, "next") } returns
            Result.success(AppointmentRequestPage(requests, nextCursor = "after"))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.Appointment.RequestHistory(businessId = businessId, limit = 10, cursor = "next"),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<AppointmentRequestPage>()
        assertEquals(requests, page.data)
        assertEquals("after", page.nextCursor)
    }

    @Test
    fun `should read the first page when no limit or cursor is given`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentRequestHistoryFilter(), null, null) } returns
            Result.success(AppointmentRequestPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.RequestHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(null, response.body<AppointmentRequestPage>().nextCursor)
    }

    @Test
    fun `should return unprocessable entity for an invalid limit`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentRequestHistoryFilter(), 0, null) } returns
            Result.failure(PageError.LimitInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.RequestHistory(businessId = businessId, limit = 0))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_LIMIT_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity for an invalid cursor`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentRequestHistoryFilter(), null, "bad") } returns
            Result.failure(PageError.CursorInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.Appointment.RequestHistory(businessId = businessId, cursor = "bad"),
        )

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_CURSOR_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the caller has no view grant`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentRequestHistoryFilter(), null, null) } returns
            Result.failure(Error.OperationNotAllowed())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.RequestHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, AppointmentRequestHistoryFilter(), null, null) } returns
            Result.failure(EmployeeAccessSuspended())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.RequestHistory(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized for the request history without authentication`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        serve(useCase, userId = null)

        whenn()
        val response = createTestClient().get(Api.Appointment.RequestHistory(businessId = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should pass every filter from the query string to the request history`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val filter = AppointmentRequestHistoryFilter(
            clientId = Uuid.random(),
            employeeId = Uuid.random(),
            statuses = setOf(AppointmentRequestStatus.PENDING, AppointmentRequestStatus.DECLINED),
            dates = HistoryDates(from = LocalDate(2027, 3, 1), to = LocalDate(2027, 3, 31)),
            order = HistoryOrder.SOONEST_FIRST,
        )
        coEvery { useCase.invoke(userId, businessId, filter, null, null) } returns
            Result.success(AppointmentRequestPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.Appointment.RequestHistory(
                businessId = businessId,
                clientId = filter.clientId,
                employeeId = filter.employeeId,
                status = listOf(AppointmentRequestStatus.PENDING, AppointmentRequestStatus.DECLINED),
                from = LocalDate(2027, 3, 1),
                to = LocalDate(2027, 3, 31),
                order = HistoryOrder.SOONEST_FIRST,
            ),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity for dates that end before they start`() = routeTest {
        given()
        val useCase: GetAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val filter = AppointmentRequestHistoryFilter(
            dates = HistoryDates(from = LocalDate(2027, 3, 2), to = LocalDate(2027, 3, 1)),
        )
        coEvery { useCase.invoke(userId, businessId, filter, null, null) } returns
            Result.failure(HistoryFilterError.DateRangeInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.Appointment.RequestHistory(
                businessId = businessId,
                from = LocalDate(2027, 3, 2),
                to = LocalDate(2027, 3, 1),
            ),
        )

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.HISTORY_DATE_RANGE_INVALID, response.body<SimpleServerError>().errorCode)
    }
}
