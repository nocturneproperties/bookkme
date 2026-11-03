package com.bookk.server.appointments.client.impl

import com.bookk.core.client.internalHttpClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ExportBusinessAppointmentsClientImplTest {

    private class SutFixture(status: HttpStatusCode = HttpStatusCode.OK) {
        val export = AppointmentsBusinessDataExport.stub(
            settings = AppointmentsBusinessDataExport.SettingsExport(automaticApproval = true, automaticCompletion = true, breakBetweenAppointmentsMinutes = 10, appointmentNote = "Bring your card"),
            appointments = listOf(
                AppointmentsBusinessDataExport.AppointmentExport(
                    id = Uuid.random(),
                    start = Instant.fromEpochMilliseconds(1_000),
                    end = Instant.fromEpochMilliseconds(2_000),
                    status = "SCHEDULED",
                    services = listOf(AppointmentsUserDataExport.ServiceExport("Haircut", "UAH 450.00", 45)),
                    employee = AppointmentsBusinessDataExport.EmployeeExport(Uuid.random(), "Olena Koval"),
                    client = AppointmentsBusinessDataExport.ClientExport(Uuid.random(), "Ivan Petrenko", null, "ivan@example.com", hasAccount = true),
                    note = "first visit",
                    cancellationReason = null,
                    priceAdjustment = null,
                    completedBy = null,
                ),
            ),
        )
        val requestedPaths = mutableListOf<String>()
        val sut = ExportBusinessAppointmentsClientImpl(
            internalHttpClient(
                engineFactory = MockEngine,
                hostnameVariable = "SERVICE_HOSTNAME",
                clientTag = "user",
                environment = { "appointments.service" },
            ) {
                addHandler { request ->
                    requestedPaths += request.url.encodedPath
                    respond(
                        content = ProtoBuf.encodeToByteArray(export),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.ProtoBuf.toString()),
                    )
                }
            },
        )
    }

    @Test
    fun `should fetch the business's appointments from appointments' internal export route`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertEquals(fixture.export, result.getOrNull())
        assertEquals(listOf("/api/internal/export/appointments/business/$businessId"), fixture.requestedPaths)
    }

    @Test
    fun `should fail when appointments can't export the data`() = runUnitTest {
        given()
        val fixture = SutFixture(status = HttpStatusCode.NotFound)

        whenn()
        val result = fixture.sut(Uuid.random())

        then()
        assertTrue(result.isFailure)
    }
}
