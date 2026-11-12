package com.bookk.appointments.microservice.route.api.internal

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ExportUserAppointmentsTest {

    private val userId = Uuid.random()

    @Test
    fun `should return the user's appointments`() = routeTest {
        given()
        val useCase: ExportUserAppointments = mockk()
        val export = AppointmentsUserDataExport.stub(
            appointments = listOf(AppointmentsUserDataExport.AppointmentExport.stub()),
        )
        coEvery { useCase.invoke(userId) } returns Result.success(export)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportUserAppointments() })

        whenn()
        val response = createTestClient().get("/api/internal/export/appointments/$userId")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(export, response.body<AppointmentsUserDataExport>())
    }

    @Test
    fun `should answer 500 when the data can't be read`() = routeTest {
        given()
        val useCase: ExportUserAppointments = mockk()
        coEvery { useCase.invoke(userId) } returns Result.failure(IllegalStateException("database unreachable"))
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportUserAppointments() })

        whenn()
        val response = createTestClient().get("/api/internal/export/appointments/$userId")

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
