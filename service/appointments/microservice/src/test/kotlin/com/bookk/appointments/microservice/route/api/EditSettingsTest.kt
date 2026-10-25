package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.appointments.domain.api.operation.EditSettings
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.domain.entity.Error
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class EditSettingsTest {

    private val testBusinessId = Uuid.random()

    @Test
    fun `should update settings successfully`() = routeTest {
        given()
        val useCase: EditSettings = mockk()
        val userId = Uuid.random()
        val update = AppointmentSettingsUpdate.stub(businessId = testBusinessId, inBetweenBreakInMinutes = 20)
        val appointmentSettings = AppointmentSettings.stub(businessId = testBusinessId)
        coEvery { useCase.invoke(userId, update) } returns Result.success(appointmentSettings)

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
            routeUnderTest = { settings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Appointment.Settings(businessId = testBusinessId)) {
            setBody(update)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(appointmentSettings, response.body<AppointmentSettings>())
    }

    @Test
    fun `should pass disabled automatic completion to the operation`() = routeTest {
        given()
        val useCase: EditSettings = mockk()
        val userId = Uuid.random()
        val update = AppointmentSettingsUpdate.stub(businessId = testBusinessId, automaticCompletion = false)
        val appointmentSettings = AppointmentSettings.stub(businessId = testBusinessId).copy(automaticCompletion = false)
        coEvery { useCase.invoke(userId, update) } returns Result.success(appointmentSettings)

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
            routeUnderTest = { settings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Appointment.Settings(businessId = testBusinessId)) {
            setBody(update)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.body<AppointmentSettings>().automaticCompletion)
    }

    @Test
    fun `should return bad request when path business id does not match body`() = routeTest {
        given()
        val useCase: EditSettings = mockk()
        val userId = Uuid.random()

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
            routeUnderTest = { settings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Appointment.Settings(businessId = testBusinessId)) {
            setBody(AppointmentSettingsUpdate.stub(businessId = Uuid.random()))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return not found when user has no permissions`() = routeTest {
        given()
        val useCase: EditSettings = mockk()
        val userId = Uuid.random()
        val update = AppointmentSettingsUpdate.stub(businessId = testBusinessId)
        coEvery { useCase.invoke(userId, update) } returns Result.failure(Error.OperationNotAllowed())

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
            routeUnderTest = { settings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Appointment.Settings(businessId = testBusinessId)) {
            setBody(update)
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when updating settings without authentication`() = routeTest {
        given()
        val useCase: EditSettings = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { settings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Appointment.Settings(businessId = testBusinessId)) {
            setBody(AppointmentSettingsUpdate.stub(businessId = testBusinessId))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
