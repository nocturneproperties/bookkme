package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.operation.GetAppointment
import com.bookk.appointments.microservice.route.AppointmentsRouting
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
import io.ktor.client.plugins.resources.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetAppointmentTest {

    @Test
    fun `should return appointment by id`() = routeTest {
        given()
        val useCase = mockk<GetAppointment>()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        coEvery { useCase(userId, appointment.id) } returns Result.success(appointment)

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
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().get(AppointmentsRouting.Api.Appointment.Id(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(appointment, response.body<Appointment>())
    }

    @Test
    fun `should return not found when appointment is missing`() = routeTest {
        given()
        val useCase = mockk<GetAppointment>()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        coEvery { useCase(userId, appointment.id) } returns Result.failure(Error.NotFound())

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
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().get(AppointmentsRouting.Api.Appointment.Id(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not found", response.bodyAsText())
    }

    @Test
    fun `should return not found when caller lacks view permission`() = routeTest {
        given()
        val useCase = mockk<GetAppointment>()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        coEvery { useCase(userId, appointment.id) } returns Result.failure(Error.OperationNotAllowed())

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
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().get(AppointmentsRouting.Api.Appointment.Id(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not found", response.bodyAsText())
    }

    @Test
    fun `should return forbidden when caller is suspended`() = routeTest {
        given()
        val useCase = mockk<GetAppointment>()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        coEvery { useCase(userId, appointment.id) } returns Result.failure(EmployeeAccessSuspended())

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
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().get(AppointmentsRouting.Api.Appointment.Id(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(200034, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return server error when operation fails unexpectedly`() = routeTest {
        given()
        val useCase = mockk<GetAppointment>()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        coEvery { useCase(userId, appointment.id) } returns Result.failure(IllegalStateException("Database unavailable"))

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
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().get(AppointmentsRouting.Api.Appointment.Id(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `should return unauthorized without authentication`() = routeTest {
        given()
        val useCase = mockk<GetAppointment>()
        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().get(AppointmentsRouting.Api.Appointment.Id(id = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }
}
