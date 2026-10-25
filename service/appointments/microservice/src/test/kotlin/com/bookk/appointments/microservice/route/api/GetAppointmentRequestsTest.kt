package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.GetPendingAppointmentRequests
import com.bookk.appointments.microservice.route.AppointmentsRouting
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAppointmentRequestsTest {

    @Test
    fun `should get appointment requests successfully`() = routeTest {
        given()
        val useCase: GetPendingAppointmentRequests = mockk()
        val businessId = Uuid.random()
        val userId = Uuid.random()
        val requests = listOf(
            AppointmentRequest(
                id = Uuid.random(),
                userId = userId,
                businessId = businessId,
                employee = EmployeeSnapshot.stub(),
                client = ClientSnapshot(Uuid.random(), "Full Name", "123456789", "test@example.com"),
                services = listOf(ServiceSnapshot(
                    Uuid.random(),
                    "Service Name",
                    Uuid.random(),
                    Money.parse("USD 100"),
                    duration = 30.minutes
                )),
                date = Instant.fromEpochMilliseconds(0),
                note = "test",
                status = AppointmentRequestStatus.PENDING,
                declineReason = ""
            )
        )

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
            diModule = module {
                single { useCase }
            },
            routeUnderTest = {
                requests()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(AppointmentsRouting.Api.Appointment.Requests(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unauthorized when getting appointment requests without authentication`() = routeTest {
        given()
        val useCase: GetPendingAppointmentRequests = mockk()
        val businessId = Uuid.random()

        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = {
                requests()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(AppointmentsRouting.Api.Appointment.Requests(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
