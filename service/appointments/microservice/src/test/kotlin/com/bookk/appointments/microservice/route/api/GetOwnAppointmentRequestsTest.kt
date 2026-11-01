package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.GetAppointmentRequests
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

internal class GetOwnAppointmentRequestsTest {

    @Test
    fun `should return own pending appointment requests`() = routeTest {
        given()
        val useCase: GetAppointmentRequests = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val requests = listOf(AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId))
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(requests)

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
            routeUnderTest = { requests() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Requests.Own(Api.Appointment.Requests(businessId = businessId)))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(requests, response.body<List<AppointmentRequest>>())
    }

    @Test
    fun `should return unauthorized when getting own pending appointment requests without authentication`() = routeTest {
        given()
        val useCase: GetAppointmentRequests = mockk()

        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { requests() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Requests.Own(Api.Appointment.Requests(businessId = Uuid.random())))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
