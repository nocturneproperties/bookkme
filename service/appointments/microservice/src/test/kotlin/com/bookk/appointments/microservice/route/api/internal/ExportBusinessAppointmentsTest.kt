package com.bookk.appointments.microservice.route.api.internal

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ExportBusinessAppointmentsTest {

    private val businessId = Uuid.random()

    @Test
    fun `should return the business's appointments`() = routeTest {
        given()
        val useCase: ExportBusinessAppointments = mockk()
        val export = AppointmentsBusinessDataExport.stub(settings = AppointmentsBusinessDataExport.SettingsExport(true, true, 10, ""))
        coEvery { useCase.invoke(businessId) } returns Result.success(export)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportBusinessAppointments() })

        whenn()
        val response = createTestClient().get("/api/internal/export/appointments/business/$businessId")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(export, response.body<AppointmentsBusinessDataExport>())
    }

    @Test
    fun `should answer 500 when the data can't be read`() = routeTest {
        given()
        val useCase: ExportBusinessAppointments = mockk()
        coEvery { useCase.invoke(businessId) } returns Result.failure(IllegalStateException("database unreachable"))
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { exportBusinessAppointments() })

        whenn()
        val response = createTestClient().get("/api/internal/export/appointments/business/$businessId")

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
