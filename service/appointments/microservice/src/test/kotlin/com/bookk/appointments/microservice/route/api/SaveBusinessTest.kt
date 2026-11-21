package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.operation.SaveBusiness
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
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class SaveBusinessTest {

    private val userId = Uuid.random()
    private val businessId = Uuid.random()

    private val authenticated: Application.() -> Unit = {
        install(Authentication) {
            provider {
                authenticate { context ->
                    context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                }
            }
        }
    }

    @Test
    fun `should save the business for the caller`() = routeTest {
        given()
        val useCase: SaveBusiness = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(Unit)
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.SavedBusiness(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return unprocessable entity when the business does not take appointments`() = routeTest {
        given()
        val useCase: SaveBusiness = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns
            Result.failure(SaveBusiness.Error.AppointmentsNotEnabled())
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.SavedBusiness(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.APPOINTMENTS_NOT_ENABLED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when the caller saved the maximum number of businesses`() = routeTest {
        given()
        val useCase: SaveBusiness = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(SaveBusiness.Error.LimitReached())
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.SavedBusiness(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            AppointmentErrorCodes.SAVED_BUSINESSES_LIMIT_REACHED,
            response.body<SimpleServerError>().errorCode,
        )
    }

    @Test
    fun `should return not found when the caller's account was deleted`() = routeTest {
        given()
        val useCase: SaveBusiness = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())
        setupApplication(
            extension = authenticated,
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.SavedBusiness(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when saving a business without authentication`() = routeTest {
        given()
        val useCase: SaveBusiness = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().post(Api.Appointment.SavedBusiness(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
