package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestPage
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.BookingPeriod
import com.bookk.appointments.domain.api.entity.OwnAppointmentRequestFilter
import com.bookk.appointments.domain.api.operation.GetOwnAppointmentRequestHistory
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

internal class GetOwnAppointmentRequestHistoryTest {

    private fun ApplicationTestBuilder.serve(useCase: GetOwnAppointmentRequestHistory, userId: Uuid?) {
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
    fun `should return a page of the caller's requests`() = routeTest {
        given()
        val useCase: GetOwnAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val requests = listOf(AppointmentRequest.stub(initiatorUserId = userId))
        coEvery { useCase.invoke(userId, OwnAppointmentRequestFilter(), 10, "next") } returns
            Result.success(AppointmentRequestPage(requests, nextCursor = "after"))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnRequestHistory(limit = 10, cursor = "next"))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<AppointmentRequestPage>()
        assertEquals(requests, page.data)
        assertEquals("after", page.nextCursor)
    }

    @Test
    fun `should read the first page of every request when nothing is given`() = routeTest {
        given()
        val useCase: GetOwnAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, OwnAppointmentRequestFilter(), null, null) } returns
            Result.success(AppointmentRequestPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnRequestHistory())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.body<AppointmentRequestPage>().nextCursor)
    }

    @Test
    fun `should pass the period and statuses from the query string`() = routeTest {
        given()
        val useCase: GetOwnAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        val statuses = listOf(AppointmentRequestStatus.DECLINED, AppointmentRequestStatus.CANCELLED)
        val filter = OwnAppointmentRequestFilter(period = BookingPeriod.PAST, statuses = statuses.toSet())
        coEvery { useCase.invoke(userId, filter, null, null) } returns
            Result.success(AppointmentRequestPage(emptyList(), nextCursor = null))
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(
            Api.Appointment.OwnRequestHistory(period = BookingPeriod.PAST, status = statuses),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity for an invalid limit`() = routeTest {
        given()
        val useCase: GetOwnAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, OwnAppointmentRequestFilter(), 0, null) } returns
            Result.failure(PageError.LimitInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnRequestHistory(limit = 0))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_LIMIT_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity for an invalid cursor`() = routeTest {
        given()
        val useCase: GetOwnAppointmentRequestHistory = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, OwnAppointmentRequestFilter(), null, "bad") } returns
            Result.failure(PageError.CursorInvalid())
        serve(useCase, userId)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnRequestHistory(cursor = "bad"))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(CoreErrorCodes.PAGE_CURSOR_INVALID, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized for the caller's requests without authentication`() = routeTest {
        given()
        val useCase: GetOwnAppointmentRequestHistory = mockk()
        serve(useCase, userId = null)

        whenn()
        val response = createTestClient().get(Api.Appointment.OwnRequestHistory())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
