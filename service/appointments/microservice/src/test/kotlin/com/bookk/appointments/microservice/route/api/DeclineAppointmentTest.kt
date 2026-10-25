package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.operation.CancelAppointment
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
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class DeclineAppointmentTest {

    @Test
    fun `should cancel appointment successfully`() = routeTest {
        given()
        val useCase: CancelAppointment = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val appointmentId = Uuid.random()
        val cancellation = AppointmentCancellation(id = appointmentId, reason = "Reason")

        coEvery { useCase.invoke(userId, cancellation) } returns Result.success(Appointment.stub(id = appointmentId))

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
        val response = client.post(Api.Appointment.Cancel(id = appointmentId)) {
            setBody(cancellation)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity when already cancelled`() = routeTest {
        given()
        val useCase: CancelAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        val cancellation = AppointmentCancellation(id = appointmentId, reason = "Reason")

        coEvery {
            useCase.invoke(
                userId,
                cancellation
            )
        } returns Result.failure(AppointmentStatusError.AlreadyCancelled())

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
        val response = client.post(Api.Appointment.Cancel(id = appointmentId)) {
            setBody(cancellation)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<SimpleServerError>()
        assertEquals(AppointmentErrorCodes.APPOINTMENT_ALREADY_CANCELED, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when already completed`() = routeTest {
        given()
        val useCase: CancelAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        val cancellation = AppointmentCancellation(id = appointmentId, reason = "Reason")

        coEvery {
            useCase.invoke(
                userId,
                cancellation
            )
        } returns Result.failure(AppointmentStatusError.AlreadyCompleted())

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
        val response = client.post(Api.Appointment.Cancel(id = appointmentId)) {
            setBody(cancellation)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<SimpleServerError>()
        assertEquals(AppointmentErrorCodes.APPOINTMENT_ALREADY_COMPLETED, body.errorCode)
    }

    @Test
    fun `should return unprocessable entity when appointment is marked as no-show`() = routeTest {
        given()
        val useCase: CancelAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        val cancellation = AppointmentCancellation(id = appointmentId, reason = "Reason")
        coEvery { useCase.invoke(userId, cancellation) } returns Result.failure(AppointmentStatusError.MarkedNoShow())

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
        val response = client.post(Api.Appointment.Cancel(id = appointmentId)) {
            setBody(cancellation)
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENT_MARKED_NO_SHOW, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when cancelling appointment without authentication`() = routeTest {
        given()
        val useCase: CancelAppointment = mockk()

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
        val response =
            client.post(Api.Appointment.Cancel(parent = Api.Appointment(parent = Api()), id = Uuid.random())) {
                setBody(AppointmentCancellation(id = Uuid.random(), reason = "Reason"))
            }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should accept a cancellation from an older client that still sends a business id`() = routeTest {
        given()
        val useCase: CancelAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, AppointmentCancellation(id = appointmentId, reason = "Reason")) } returns Result.success(Appointment.stub(id = appointmentId))

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
        val response = client.post(Api.Appointment.Cancel(id = appointmentId)) {
            setBody(LegacyCancelAppointmentBody(id = appointmentId, businessId = Uuid.random(), reason = "Reason"))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

@Serializable
private class LegacyCancelAppointmentBody(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val reason: String
)
