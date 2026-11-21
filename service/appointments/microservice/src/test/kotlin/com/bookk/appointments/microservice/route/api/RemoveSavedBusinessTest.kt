package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.operation.RemoveSavedBusiness
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.plugins.resources.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class RemoveSavedBusinessTest {

    @Test
    fun `should remove the business from the caller's saved businesses`() = routeTest {
        given()
        val useCase: RemoveSavedBusiness = mockk()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(Unit)
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
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().delete(Api.Appointment.SavedBusiness(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, businessId) }
    }

    @Test
    fun `should return unauthorized when removing a saved business without authentication`() = routeTest {
        given()
        val useCase: RemoveSavedBusiness = mockk()
        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val response = createTestClient().delete(Api.Appointment.SavedBusiness(businessId = Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
