package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.microservice.route.AppointmentsRouting
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
import io.ktor.client.request.setBody
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

internal class CreateInstantAppointmentTest {

    private fun buildAppointment(userId: Uuid): Appointment = Appointment(
        id = Uuid.random(),
        userId = userId,
        businessId = Uuid.random(),
        employee = EmployeeSnapshot.stub(),
        client = ClientSnapshot(Uuid.random(), "Full Name", "123456789", "test@example.com"),
        services = listOf(
            ServiceSnapshot(
                Uuid.random(),
                "Service Name",
                Uuid.random(),
                Money.parse("USD 100"),
                duration = 30.minutes
            )
        ),
        date = Instant.fromEpochMilliseconds(0),
        note = "test",
        status = AppointmentStatus.SCHEDULED,
        cancellationReason = "",
        completedBy = null,
        priceAdjustment = null
    )

    @Test
    fun `should create instant appointment successfully`() = routeTest {
        given()
        val useCase: CreateAppointment = mockk()
        val userId = Uuid.random()
        val appointment = buildAppointment(userId)

        coEvery { useCase.invoke(userId, appointment, true) } returns Result.success(appointment)

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
                appointment()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(AppointmentsRouting.Api.Appointment.Instant()) {
            setBody(appointment)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status, "Response status: ${response.status}")
    }

    @Test
    fun `should return unprocessable entity when appointment exists`() = routeTest {
        given()
        val useCase: CreateAppointment = mockk()
        val userId = Uuid.random()
        val appointment = buildAppointment(userId)

        coEvery { useCase.invoke(userId, appointment, true) } returns Result.failure(CreateAppointment.Error.AppointmentForThisTimeExists())

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
                appointment()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(AppointmentsRouting.Api.Appointment.Instant()) {
            setBody(appointment)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<SimpleServerError>()
        assertEquals(AppointmentErrorCodes.APPOINTMENT_EXISTS, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when time not allowed`() = routeTest {
        given()
        val useCase: CreateAppointment = mockk()
        val userId = Uuid.random()
        val appointment = buildAppointment(userId)

        coEvery { useCase.invoke(userId, appointment, true) } returns Result.failure(CreateAppointment.Error.RequestForThisTimeNotAllowed())

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
                appointment()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(AppointmentsRouting.Api.Appointment.Instant()) {
            setBody(appointment)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<SimpleServerError>()
        assertEquals(AppointmentErrorCodes.TIME_NOT_ALLOWED, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when date not allowed`() = routeTest {
        given()
        val useCase: CreateAppointment = mockk()
        val userId = Uuid.random()
        val appointment = buildAppointment(userId)

        coEvery { useCase.invoke(userId, appointment, true) } returns Result.failure(CreateAppointment.Error.RequestForThisDateNotAllowed())

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
                appointment()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(AppointmentsRouting.Api.Appointment.Instant()) {
            setBody(appointment)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<SimpleServerError>()
        assertEquals(AppointmentErrorCodes.DATE_NOT_ALLOWED, body.errorCode)
    }

    @Test
    fun `should return unauthorized when creating instant appointment without authentication`() = routeTest {
        given()
        val useCase: CreateAppointment = mockk()

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
                appointment()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(AppointmentsRouting.Api.Appointment.Instant()) {
            setBody(buildAppointment(Uuid.random()))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
