package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.UpcomingBookings
import com.bookk.appointments.domain.api.operation.GetUpcomingBookings
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
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
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetUpcomingBookingsTest {

    @Test
    fun `should return the caller's upcoming appointments and requests`() = routeTest {
        given()
        val useCase: GetUpcomingBookings = mockk()
        val userId = Uuid.random()
        val bookings = UpcomingBookings.stub()
        coEvery { useCase.invoke(userId) } returns Result.success(bookings)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Upcoming())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(bookings, response.body<UpcomingBookings>())
    }

    @Test
    fun `should return empty lists for a caller without bookings`() = routeTest {
        given()
        val useCase: GetUpcomingBookings = mockk()
        val userId = Uuid.random()
        val bookings = UpcomingBookings.stub(appointments = emptyList(), requests = emptyList())
        coEvery { useCase.invoke(userId) } returns Result.success(bookings)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Upcoming())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(bookings, response.body<UpcomingBookings>())
    }

    @Test
    fun `should return unauthorized for upcoming bookings without authentication`() = routeTest {
        given()
        val useCase: GetUpcomingBookings = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Upcoming())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
