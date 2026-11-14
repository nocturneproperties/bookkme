package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.RequestClientSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.AppointmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.ClientExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.EmployeeExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.RequestExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.SettingsExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.PriceAdjustmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ServiceExport
import io.mockk.coEvery
import io.mockk.mockk
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ExportBusinessAppointmentsImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut =
            ExportBusinessAppointmentsImpl(
                appointmentDataSource,
                requestDataSource,
                settingsDataSource,
                transactionManager,
            )
        val businessId = Uuid.random()
        val readInTransaction = mutableListOf<Boolean>()
        val service = ServiceSnapshot(Uuid.random(), "Haircut", Uuid.random(), Money.parse("UAH 450.00"), 45.minutes)
        val employee = EmployeeSnapshot.stub().copy(fullName = "Olena Koval")
        val client = ClientSnapshot.stub(
            userId = Uuid.random(),
        ).copy(fullName = "Ivan Petrenko", phone = "+380509998877", email = "ivan@example.com")
        val requestingClient = RequestClientSnapshot.stub()
            .copy(fullName = "Ivan Petrenko", phone = "+380509998877", email = "ivan@example.com")

        init {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getAll(businessId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                emptyList()
            }
            coEvery { requestDataSource.getPending(businessId) } returns emptyList()
            coEvery { settingsDataSource.get(businessId) } returns null
        }
    }

    @Test
    fun `should export every appointment of the business with its client, employee, notes and reasons`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val appointment = Appointment.stub(
            businessId = fixture.businessId,
            date = Instant.fromEpochMilliseconds(3_600_000),
        ).copy(
            employee = fixture.employee,
            client = fixture.client,
            services = listOf(fixture.service),
            status = AppointmentStatus.COMPLETED,
            note = "allergic to latex",
            cancellationReason = "",
            completedBy = AppointmentCompletedBy.USER,
            priceAdjustment = PriceAdjustment(
                additionalServices = emptyList(),
                price = Money.parse("UAH 500.00"),
                reason = "extra styling",
            ),
        )
        coEvery { fixture.appointmentDataSource.getAll(fixture.businessId) } returns listOf(appointment)

        whenn()
        val export = fixture.sut(fixture.businessId).getOrThrow()

        then()
        assertEquals(
            listOf(
                AppointmentExport(
                    id = appointment.id,
                    start = appointment.date,
                    end = appointment.dateEnd,
                    status = "COMPLETED",
                    services = listOf(ServiceExport("Haircut", "UAH 450.00", 45)),
                    employee = EmployeeExport(fixture.employee.id, "Olena Koval"),
                    client = ClientExport(
                        fixture.client.id,
                        "Ivan Petrenko",
                        "+380509998877",
                        "ivan@example.com",
                        hasAccount = true,
                    ),
                    note = "allergic to latex",
                    cancellationReason = null,
                    priceAdjustment = PriceAdjustmentExport("UAH 500.00", emptyList(), "extra styling"),
                    completedBy = "USER",
                ),
            ),
            export.appointments,
        )
    }

    @Test
    fun `should export the pending requests of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = AppointmentRequest.stub(businessId = fixture.businessId, employee = fixture.employee).copy(
            client = fixture.requestingClient,
            services = listOf(fixture.service),
            note = "",
        )
        coEvery { fixture.requestDataSource.getPending(fixture.businessId) } returns listOf(request)

        whenn()
        val export = fixture.sut(fixture.businessId).getOrThrow()

        then()
        assertEquals(
            listOf(
                RequestExport(
                    id = request.id,
                    start = request.date,
                    end = request.dateEnd,
                    services = listOf(ServiceExport("Haircut", "UAH 450.00", 45)),
                    employee = EmployeeExport(fixture.employee.id, "Olena Koval"),
                    client = ClientExport(
                        fixture.requestingClient.id,
                        "Ivan Petrenko",
                        "+380509998877",
                        "ivan@example.com",
                        hasAccount = true,
                    ),
                    note = null,
                ),
            ),
            export.pendingRequests,
        )
    }

    @Test
    fun `should export the business's appointment settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settingsDataSource.get(fixture.businessId) } returns
            AppointmentSettings.stub(fixture.businessId).copy(
                automaticApproval = true,
                automaticCompletion = false,
                inBetweenBreakInMinutes = 15,
                appointmentNote = "Bring your card",
            )

        whenn()
        val export = fixture.sut(fixture.businessId).getOrThrow()

        then()
        assertEquals(
            SettingsExport(
                automaticApproval = true,
                automaticCompletion = false,
                breakBetweenAppointmentsMinutes = 15,
                appointmentNote = "Bring your card",
            ),
            export.settings,
        )
    }

    @Test
    fun `should export no settings for a business that never enabled appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val export = fixture.sut(fixture.businessId).getOrThrow()

        then()
        assertNull(export.settings)
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail when the appointments can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.appointmentDataSource.getAll(fixture.businessId) } throws
            IllegalStateException("database unreachable")

        whenn()
        val result = fixture.sut(fixture.businessId)

        then()
        assertTrue(result.isFailure)
    }
}
