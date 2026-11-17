package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatusError
import com.bookk.appointments.domain.api.operation.WithdrawAppointmentRequest
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class WithdrawAppointmentRequestTest {

    private fun ApplicationTestBuilder.setupWithdrawal(
        userId: Uuid,
        requestId: Uuid,
        outcome: Result<Unit>,
    ): WithdrawAppointmentRequest {
        val useCase: WithdrawAppointmentRequest = mockk()
        coEvery { useCase.invoke(userId, requestId) } returns outcome
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
        return useCase
    }

    @Test
    fun `should withdraw the caller's pending request`() = routeTest {
        given()
        val userId = Uuid.random()
        val requestId = Uuid.random()
        val useCase = setupWithdrawal(userId, requestId, Result.success(Unit))

        whenn()
        val response = createTestClient().post(Api.Appointment.RequestWithdraw(id = requestId))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase(userId, requestId) }
    }

    @Test
    fun `should return not found when the request is unknown or someone else's`() = routeTest {
        given()
        val userId = Uuid.random()
        val requestId = Uuid.random()
        setupWithdrawal(userId, requestId, Result.failure(Error.NotFound()))

        whenn()
        val response = createTestClient().post(Api.Appointment.RequestWithdraw(id = requestId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unprocessable entity when the request was already approved`() = routeTest {
        given()
        val userId = Uuid.random()
        val requestId = Uuid.random()
        setupWithdrawal(userId, requestId, Result.failure(AppointmentRequestStatusError.AlreadyApproved()))

        whenn()
        val response = createTestClient().post(Api.Appointment.RequestWithdraw(id = requestId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.REQUEST_ALREADY_APPROVED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the request was already declined`() = routeTest {
        given()
        val userId = Uuid.random()
        val requestId = Uuid.random()
        setupWithdrawal(userId, requestId, Result.failure(AppointmentRequestStatusError.AlreadyDeclined()))

        whenn()
        val response = createTestClient().post(Api.Appointment.RequestWithdraw(id = requestId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.REQUEST_ALREADY_DECLINED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when withdrawing without authentication`() = routeTest {
        given()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { },
            routeUnderTest = { requests() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.RequestWithdraw(id = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
