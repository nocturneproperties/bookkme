package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.operation.GetSettings
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
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.PermissionErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetSettingsTest {

    private val testBusinessId = Uuid.random()

    @Test
    fun `should get settings successfully`() = routeTest {
        given()
        val useCase: GetSettings = mockk()
        val userId = Uuid.random()
        val appointmentSettings = AppointmentSettings.stub(businessId = testBusinessId)
        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.success(appointmentSettings)

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
        val response = client.get(Api.Appointment.Settings(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: GetSettings = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.failure(EmployeeAccessSuspended())

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
        val response = client.get(Api.Appointment.Settings(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when getting settings without authentication`() = routeTest {
        given()
        val useCase: GetSettings = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { settings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Settings(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
