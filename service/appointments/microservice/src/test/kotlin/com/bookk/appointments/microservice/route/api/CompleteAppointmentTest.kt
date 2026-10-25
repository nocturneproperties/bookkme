package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.domain.entity.BusinessError
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
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.PermissionErrorCodes
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class CompleteAppointmentTest {

    @Test
    fun `should complete appointment successfully`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        coEvery { useCase.invoke(userId, appointment.id, null) } returns Result.success(appointment)

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
        val response = client.post(Api.Appointment.Complete(id = appointment.id))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<Appointment>()
        assertEquals(AppointmentStatus.COMPLETED, body.status)
        assertEquals(AppointmentCompletedBy.USER, body.completedBy)
    }

    @Test
    fun `should return unprocessable entity when appointment is cancelled`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId, null) } returns Result.failure(AppointmentStatusError.AlreadyCancelled())

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
        val response = client.post(Api.Appointment.Complete(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENT_ALREADY_CANCELED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when appointment has not started yet`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId, null) } returns Result.failure(AppointmentStatusError.NotStarted())

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
        val response = client.post(Api.Appointment.Complete(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENT_NOT_STARTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when appointment is marked as no-show`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId, null) } returns Result.failure(AppointmentStatusError.MarkedNoShow())

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
        val response = client.post(Api.Appointment.Complete(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENT_MARKED_NO_SHOW, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when caller lacks permission`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId, null) } returns Result.failure(Error.OperationNotAllowed())

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
        val response = client.post(Api.Appointment.Complete(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        coEvery { useCase.invoke(userId, appointmentId, null) } returns Result.failure(EmployeeAccessSuspended())

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
        val response = client.post(Api.Appointment.Complete(id = appointmentId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when completing without authentication`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()

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
        val response = client.post(Api.Appointment.Complete(id = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    @Test
    fun `should complete appointment with price adjustment`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val draft = PriceAdjustmentDraft.stub(additionalServiceIds = listOf(Uuid.random()), price = Money.parse("USD 80"), reason = "Discount")
        val adjustment = PriceAdjustment.stub(price = draft.price, reason = draft.reason)
        val appointment = Appointment.stub().copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.USER,
            priceAdjustment = adjustment
        )
        coEvery { useCase.invoke(userId, appointment.id, draft) } returns Result.success(appointment)

        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Complete(id = appointment.id)) {
            setBody(CompleteAppointmentRequest(priceAdjustment = draft))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(adjustment, response.body<Appointment>().priceAdjustment)
    }

    @Test
    fun `should complete appointment without price adjustment when body carries none`() = routeTest {
        given()
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.USER)
        coEvery { useCase.invoke(userId, appointment.id, null) } returns Result.success(appointment)

        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Complete(id = appointment.id)) {
            setBody(CompleteAppointmentRequest(priceAdjustment = null))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unprocessable entity when adjusted price is negative`() = routeTest {
        given()
        assertAdjustmentRejected(CompleteAppointment.Error.NegativePrice(), AppointmentErrorCodes.PRICE_ADJUSTMENT_NEGATIVE_PRICE)
    }

    @Test
    fun `should return unprocessable entity when adjusted price currency differs from appointment currency`() = routeTest {
        given()
        assertAdjustmentRejected(CompleteAppointment.Error.CurrencyMismatch(), AppointmentErrorCodes.PRICE_ADJUSTMENT_CURRENCY_MISMATCH)
    }

    @Test
    fun `should return unprocessable entity when adjustment reason is too long`() = routeTest {
        given()
        assertAdjustmentRejected(CompleteAppointment.Error.ReasonTooLong(), AppointmentErrorCodes.PRICE_ADJUSTMENT_REASON_TOO_LONG)
    }

    @Test
    fun `should return unprocessable entity when an additional service is not found`() = routeTest {
        given()
        val serviceNotFoundCode = 200013
        assertAdjustmentRejected(
            BusinessError(HttpStatusCode.UnprocessableEntity.value, serviceNotFoundCode, "One or more services not found"),
            serviceNotFoundCode
        )
    }

    private suspend fun ApplicationTestBuilder.assertAdjustmentRejected(error: Throwable, expectedCode: Int) {
        val useCase: CompleteAppointment = mockk()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        val draft = PriceAdjustmentDraft.stub()
        coEvery { useCase.invoke(userId, appointmentId, draft) } returns Result.failure(error)

        setupApplication(
            extension = { authenticateAs(userId) },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Complete(id = appointmentId)) {
            setBody(CompleteAppointmentRequest(priceAdjustment = draft))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(expectedCode, response.body<SimpleServerError>().errorCode)
    }

    private fun Application.authenticateAs(userId: Uuid) {
        install(Authentication) {
            provider {
                authenticate { context ->
                    context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                }
            }
        }
    }
}
