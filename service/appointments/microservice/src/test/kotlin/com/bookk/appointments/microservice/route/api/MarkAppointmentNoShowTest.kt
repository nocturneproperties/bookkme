package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.operation.MarkAppointmentNoShow
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
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

internal class MarkAppointmentNoShowTest {

    @Test
    fun `should mark appointment as no-show successfully`() = routeTest {
        given()
        val useCase: MarkAppointmentNoShow = mockk()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.NO_SHOW)
        coEvery { useCase.invoke(userId, appointment.id) } returns Result.success(appointment)

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
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.NoShow(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AppointmentStatus.NO_SHOW, response.body<Appointment>().status)
    }

    @Test
    fun `should return unprocessable entity when appointment is cancelled`() = routeTest {
        given()
        val useCase: MarkAppointmentNoShow = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId) } returns Result.failure(AppointmentStatusError.AlreadyCancelled())

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
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.NoShow(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENT_ALREADY_CANCELED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when appointment has not started yet`() = routeTest {
        given()
        val useCase: MarkAppointmentNoShow = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId) } returns Result.failure(AppointmentStatusError.NotStarted())

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
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.NoShow(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENT_NOT_STARTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when caller lacks permission`() = routeTest {
        given()
        val useCase: MarkAppointmentNoShow = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId) } returns Result.failure(Error.OperationNotAllowed())

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
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.NoShow(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: MarkAppointmentNoShow = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId) } returns Result.failure(EmployeeAccessSuspended())

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
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.NoShow(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when marking no-show without authentication`() = routeTest {
        given()
        val useCase: MarkAppointmentNoShow = mockk()

        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.NoShow(id = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
