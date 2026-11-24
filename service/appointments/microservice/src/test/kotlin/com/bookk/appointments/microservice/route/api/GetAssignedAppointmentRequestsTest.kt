package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.operation.GetAssignedAppointmentRequests
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
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
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.PermissionErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetAssignedAppointmentRequestsTest {

    @Test
    fun `should return pending appointment requests assigned to the caller`() = routeTest {
        given()
        val useCase: GetAssignedAppointmentRequests = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val requests = listOf(
            AppointmentRequest.stub(businessId = businessId, employee = EmployeeSnapshot.stub(userId = userId)),
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
            diModule = module { single { useCase } },
            routeUnderTest = { requests() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(
            Api.Appointment.Requests.Assigned(Api.Appointment.Requests(businessId = businessId)),
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(requests, response.body<List<AppointmentRequest>>())
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: GetAssignedAppointmentRequests = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(EmployeeAccessSuspended())

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
            routeUnderTest = { requests() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(
            Api.Appointment.Requests.Assigned(Api.Appointment.Requests(businessId = businessId)),
        )

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized for assigned appointment requests without authentication`() = routeTest {
        given()
        val useCase: GetAssignedAppointmentRequests = mockk()

        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { requests() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(
            Api.Appointment.Requests.Assigned(Api.Appointment.Requests(businessId = Uuid.random())),
        )

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
