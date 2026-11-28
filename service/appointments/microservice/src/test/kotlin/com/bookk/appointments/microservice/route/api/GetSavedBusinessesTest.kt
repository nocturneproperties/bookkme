package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.SavedBusiness
import com.bookk.appointments.domain.api.operation.GetSavedBusinesses
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetSavedBusinessesTest {

    @Test
    fun `should return the caller's saved businesses`() = routeTest {
        given()
        val useCase: GetSavedBusinesses = mockk()
        val userId = Uuid.random()
        val saved = listOf(
            SavedBusiness.stub(lastVisitAt = Instant.fromEpochMilliseconds(3_600_000)),
            SavedBusiness.stub(enabled = false),
        )
        coEvery { useCase.invoke(userId) } returns Result.success(saved)

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
        val client = createTestClient()
        val response = client.get(Api.Appointment.SavedBusinesses())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(saved, response.body<List<SavedBusiness>>())
    }

    @Test
    fun `should return unauthorized for saved businesses without authentication`() = routeTest {
        given()
        val useCase: GetSavedBusinesses = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { clientBookings() },
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.SavedBusinesses())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
