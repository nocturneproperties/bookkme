package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.BusinessAppointmentsEnabled
import com.bookk.appointments.domain.api.operation.EnableAppointmentsForBusiness
import com.bookk.appointments.domain.api.operation.GetClientBusinessesAppointmentsStatus
import com.bookk.appointments.domain.api.operation.IsAppointmentsEnabled
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
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
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class EnableAppointmentsTest {

    private val testBusinessId = Uuid.random()

    @Test
    fun `should enable appointments successfully`() = routeTest {
        given()
        val useCase: EnableAppointmentsForBusiness = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.success(Unit)

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
                appointmentInit()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return isEnabled status successfully`() = routeTest {
        given()
        val useCase: IsAppointmentsEnabled = mockk()
        val userId = Uuid.random()

        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.success(true)

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
                appointmentInit()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.body<Boolean>())
    }

    @Test
    fun `should return appointments enabled status for client businesses`() = routeTest {
        given()
        val useCase: GetClientBusinessesAppointmentsStatus = mockk()
        val userId = Uuid.random()
        val statuses = listOf(
            BusinessAppointmentsEnabled.stub(enabled = true),
            BusinessAppointmentsEnabled.stub(enabled = false)
        )
        coEvery { useCase.invoke(userId) } returns Result.success(statuses)

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
                appointmentInit()
            }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.EnabledForClientBusinesses())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(statuses, response.body<List<BusinessAppointmentsEnabled>>())
    }

    @Test
    fun `should return unprocessable entity when appointments already enabled`() = routeTest {
        given()
        val useCase: EnableAppointmentsForBusiness = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.failure(EnableAppointmentsForBusiness.Error.AlreadyEnabled())

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
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(AppointmentErrorCodes.PLUGIN_ALREADY_ENABLED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the business service does not know the business`() = routeTest {
        given()
        val useCase: EnableAppointmentsForBusiness = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.failure(Error.NotFound())

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
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return not found when caller does not own the business`() = routeTest {
        given()
        val useCase: EnableAppointmentsForBusiness = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, testBusinessId) } returns Result.failure(Error.OperationNotAllowed())

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
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended in the business`() = routeTest {
        given()
        val useCase: EnableAppointmentsForBusiness = mockk()
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
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when enabling appointments without authentication`() = routeTest {
        given()
        val useCase: EnableAppointmentsForBusiness = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when checking if appointments are enabled without authentication`() = routeTest {
        given()
        val useCase: IsAppointmentsEnabled = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.Enabled(businessId = testBusinessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when checking client businesses appointments status without authentication`() = routeTest {
        given()
        val useCase: GetClientBusinessesAppointmentsStatus = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { appointmentInit() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Appointment.EnabledForClientBusinesses())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
