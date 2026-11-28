package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentPage
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BookingPeriod
import com.bookk.appointments.domain.api.entity.OwnAppointmentFilter
import com.bookk.appointments.domain.api.operation.GetOwnAppointmentHistory
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.domain.entity.CoreErrorCodes
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetOwnAppointmentHistoryTest {

    private fun ApplicationTestBuilder.serve(useCase: GetOwnAppointmentHistory, userId: Uuid?) {
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
            routeUnderTest = { clientBookings() },
        )
    }

    @Test
    fun `should return a page of the caller's appointments`() = routeTest {
        given()
        val useCase: GetOwnAppointmentHistory = mockk()
        val userId = Uuid.random()
        val appointments = listOf(Appointment.stub(note = ""))
        coEvery { useCase.invoke(userId, OwnAppointmentFilter(), 10, "next") } returns
            Result.success(AppointmentPage(appointments, nextCursor = "after"))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnHistory(limit = 10, cursor = "next"))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<AppointmentPage>()
        assertEquals(appointments, page.data)
        assertEquals("after", page.nextCursor)
    }

    @Test
    fun `should read the first page of every appointment when nothing is given`() = routeTest {
        given()
        val useCase: GetOwnAppointmentHistory = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, OwnAppointmentFilter(), null, null) } returns
            Result.success(AppointmentPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnHistory())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.body<AppointmentPage>().nextCursor)
    }

    @Test
    fun `should pass the period and statuses from the query string`() = routeTest {
        given()
        val useCase: GetOwnAppointmentHistory = mockk()
        val userId = Uuid.random()
        val statuses = listOf(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
        val filter = OwnAppointmentFilter(period = BookingPeriod.PAST, statuses = statuses.toSet())
        coEvery { useCase.invoke(userId, filter, null, null) } returns
            Result.success(AppointmentPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.Appointment.OwnHistory(period = BookingPeriod.PAST, status = statuses),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity for an invalid limit`() = routeTest {
        given()
        val useCase: GetOwnAppointmentHistory = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, OwnAppointmentFilter(), 0, null) } returns
            Result.failure(PageError.LimitInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnHistory(limit = 0))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_LIMIT_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity for an invalid cursor`() = routeTest {
        given()
        val useCase: GetOwnAppointmentHistory = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, OwnAppointmentFilter(), null, "bad") } returns
            Result.failure(PageError.CursorInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnHistory(cursor = "bad"))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_CURSOR_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized for the caller's appointments without authentication`() = routeTest {
        given()
        val useCase: GetOwnAppointmentHistory = mockk()
        serve(useCase, userId = null)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnHistory())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
