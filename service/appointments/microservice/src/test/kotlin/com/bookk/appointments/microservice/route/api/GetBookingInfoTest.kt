package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.BookingInfo
import com.bookk.appointments.domain.api.operation.GetBookingInfo
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.domain.entity.Error
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

internal class GetBookingInfoTest {

    @Test
    fun `should return the business's booking info`() = routeTest {
        given()
        val useCase: GetBookingInfo = mockk()
        val businessId = Uuid.random()
        val info = BookingInfo.stub(businessId = businessId, appointmentNote = "Arrive early")
        coEvery { useCase.invoke(businessId) } returns Result.success(info)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.BookingInfo(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(info, response.body<BookingInfo>())
    }

    @Test
    fun `should return not found when the business does not take appointments`() = routeTest {
        given()
        val useCase: GetBookingInfo = mockk()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(businessId) } returns Result.failure(Error.NotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.BookingInfo(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized for booking info without authentication`() = routeTest {
        given()
        val useCase: GetBookingInfo = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.BookingInfo(businessId = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
