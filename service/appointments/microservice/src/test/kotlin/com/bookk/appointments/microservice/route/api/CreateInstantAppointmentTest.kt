package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.entity.InstantAppointmentDraft
import com.bookk.appointments.domain.api.operation.CreateInstantAppointment
import com.bookk.appointments.microservice.route.AppointmentsRouting
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class CreateInstantAppointmentTest {

    private val userId = Uuid.random()
    private val draft = InstantAppointmentDraft.stub()

    private fun ApplicationTestBuilder.instantRoute(useCase: CreateInstantAppointment): HttpClient {
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
        return createTestClient()
    }

    private suspend fun HttpClient.postDraft(): HttpResponse = post(AppointmentsRouting.Api.Appointment.Instant()) {
        setBody(draft)
    }

    private suspend fun ApplicationTestBuilder.respondsWithError(error: Throwable): HttpResponse {
        val useCase: CreateInstantAppointment = mockk()
        coEvery { useCase.invoke(userId, draft) } returns Result.failure(error)
        return instantRoute(useCase).postDraft()
    }

    private suspend fun HttpResponse.assertError(status: HttpStatusCode, code: Int) {
        assertEquals(status, this.status)
        assertEquals(code, body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should book the draft for the caller and return the appointment`() = routeTest {
        given()
        val useCase: CreateInstantAppointment = mockk()
        val appointment = Appointment.stub()
        coEvery { useCase.invoke(userId, draft) } returns Result.success(appointment)
        val client = instantRoute(useCase)

        whenn()
        val response = client.postDraft()

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(appointment.id, response.body<Appointment>().id)
    }

    @Test
    fun `should return unprocessable entity when appointment exists`() = routeTest {
        given()
        val error = CreateInstantAppointment.Error.AppointmentForThisTimeExists()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, AppointmentErrorCodes.APPOINTMENT_EXISTS)
    }

    @Test
    fun `should return unprocessable entity when time not allowed`() = routeTest {
        given()
        val error = CreateInstantAppointment.Error.RequestForThisTimeNotAllowed()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, AppointmentErrorCodes.TIME_NOT_ALLOWED)
    }

    @Test
    fun `should return unprocessable entity when date not allowed`() = routeTest {
        given()
        val error = CreateInstantAppointment.Error.RequestForThisDateNotAllowed()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, AppointmentErrorCodes.DATE_NOT_ALLOWED)
    }

    @Test
    fun `should return unprocessable entity when the date is in the past`() = routeTest {
        given()
        val error = CreateInstantAppointment.Error.DateInThePastNotAllowed()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, AppointmentErrorCodes.DATE_IN_PAST)
    }

    @Test
    fun `should return unprocessable entity when the quote token was already used`() = routeTest {
        given()
        val error = BookingQuoteError.TokenAlreadyUsed()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, AppointmentErrorCodes.QUOTE_TOKEN_ALREADY_USED)
    }

    @Test
    fun `should return bad request when the price changed since the quote`() = routeTest {
        given()
        val error = BookingQuoteError.PriceChanged()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.BadRequest, AppointmentErrorCodes.PRICE_CHANGED)
    }

    @Test
    fun `should return bad request when the duration changed since the quote`() = routeTest {
        given()
        val error = BookingQuoteError.DurationChanged()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.BadRequest, AppointmentErrorCodes.DURATION_CHANGED)
    }

    @Test
    fun `should return bad request when the quote token does not match the services`() = routeTest {
        given()
        val error = BookingQuoteError.ServicesSignatureMiss()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.BadRequest, AppointmentErrorCodes.SERVICES_VALIDATION_FAILED)
    }

    @Test
    fun `should return not found when the client is not a client of the business`() = routeTest {
        given()
        val error = GetStaffBookingContext.Error.ClientNotFound()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.NotFound, BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS)
    }

    @Test
    fun `should return not found when the employee is not in the business`() = routeTest {
        given()
        val error = GetStaffBookingContext.Error.EmployeeNotFound()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.NotFound, BusinessErrorCodes.BUSINESS_EMPLOYEE_NOT_EXISTS)
    }

    @Test
    fun `should return unprocessable entity when the employee is suspended`() = routeTest {
        given()
        val error = GetStaffBookingContext.Error.EmployeeSuspended()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, BusinessErrorCodes.BUSINESS_EMPLOYEE_SUSPENDED)
    }

    @Test
    fun `should return unprocessable entity when a service is not in the business`() = routeTest {
        given()
        val error = GetStaffBookingContext.Error.ServiceNotFound()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND)
    }

    @Test
    fun `should return unprocessable entity when no services are requested`() = routeTest {
        given()
        val error = GetStaffBookingContext.Error.EmptyServiceList()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.UnprocessableEntity, BusinessErrorCodes.BUSINESS_QUOTE_EMPTY_SERVICE_LIST)
    }

    @Test
    fun `should return forbidden when the caller is a suspended employee`() = routeTest {
        given()
        val error = EmployeeAccessSuspended()

        whenn()
        val response = respondsWithError(error)

        then()
        response.assertError(HttpStatusCode.Forbidden, BusinessErrorCodes.BUSINESS_EMPLOYEE_ACCESS_SUSPENDED)
    }

    @Test
    fun `should return not found when the caller may not book or the client or their account is gone`() = routeTest {
        given()
        val error = Error.OperationNotAllowed()

        whenn()
        val response = respondsWithError(error)

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should reject the appointment body older apps send`() = routeTest {
        given()
        val useCase: CreateInstantAppointment = mockk()
        val client = instantRoute(useCase)

        whenn()
        val response = client.post(AppointmentsRouting.Api.Appointment.Instant()) { setBody(Appointment.stub()) }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }

    @Test
    fun `should return unauthorized when creating instant appointment without authentication`() = routeTest {
        given()
        val useCase: CreateInstantAppointment = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { appointment() },
        )

        whenn()
        val response = createTestClient().postDraft()

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
