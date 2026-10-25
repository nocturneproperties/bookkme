package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentPagination
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.microservice.route.AppointmentsRouting
import com.bookk.core.domain.entity.PaginationMetadata
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
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
import kotlin.uuid.Uuid

internal class GetAppointmentHistoryTest {

    @Test
    fun `should get appointment history successfully`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val businessId = Uuid.random()
        val userId = Uuid.random()
        val appointments = listOf(Appointment.stub(userId = userId, businessId = businessId))
        val pagination = AppointmentPagination(
            data = appointments,
            metadata = PaginationMetadata(total = 1L, page = 0L, pageSize = 50)
        )

        coEvery { useCase.invoke(userId, businessId, 50, 0, null) } returns Result.success(pagination)

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
        val response = client.get(AppointmentsRouting.Api.AppointmentHistory(businessId = businessId, limit = 50, offset = 0))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should get appointment history filtered by query`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val businessId = Uuid.random()
        val userId = Uuid.random()
        val appointments = listOf(Appointment.stub(userId = userId, businessId = businessId))
        val pagination = AppointmentPagination(
            data = appointments,
            metadata = PaginationMetadata(total = 1L, page = 0L, pageSize = 50)
        )

        coEvery {
            useCase.invoke(userId, businessId, 50, 0, "John")
        } returns Result.success(pagination)

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
        val response = client.get(
            AppointmentsRouting.Api.AppointmentHistory(
                businessId = businessId,
                limit = 50,
                offset = 0,
                query = "John"
            )
        )

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return unauthorized when getting appointment history without authentication`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val businessId = Uuid.random()

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
        val response = client.get(AppointmentsRouting.Api.AppointmentHistory(businessId = businessId, limit = 50, offset = 0))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return server error when getting appointment history fails`() = routeTest {
        given()
        val useCase: GetAppointmentHistory = mockk()
        val businessId = Uuid.random()
        val userId = Uuid.random()

        coEvery {
            useCase.invoke(userId, businessId, 50, 0, null)
        } returns Result.failure(Exception("Database error"))

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
        val response = client.get(AppointmentsRouting.Api.AppointmentHistory(businessId = businessId, limit = 50, offset = 0))

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
