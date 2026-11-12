package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable, WorkingHoursTable, DayOffsTable, SettingsTable,
            AppointmentTable, AppointmentServicesTable, PriceAdjustmentTable, PriceAdjustmentServicesTable,
            AppointmentRequestTable, AppointmentRequestServicesTable,
        )
        val sut = UserDataExportDataSourceImpl()
        val appointmentSut = AppointmentDataSourceImpl()
        val requestSut = AppointmentRequestDataSourceImpl(InMemoryCacheClient())
        val subscriptionSut = AppointmentSubscriptionDataSourceImpl()
        val userId = Uuid.random()

        suspend fun business(name: String = "Studio"): Uuid {
            val snapshot = BusinessSnapshot.stub(name = name)
            suspendTransaction { subscriptionSut.attachBusiness(snapshot) }
            return snapshot.id
        }

        suspend fun appointment(appointment: Appointment): Uuid = suspendTransaction {
            appointmentSut.create(appointment)
        }.id

        suspend fun request(request: AppointmentRequest): Uuid = suspendTransaction { requestSut.create(request) }.id
    }

    @Test
    fun `should list the appointments where the user is the client, the employee or the booker`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.business()
        val other = Uuid.random()
        val asClient = fixture.appointment(
            Appointment.stub(
                initiatorUserId = other,
                businessId = businessId,
            ).copy(client = ClientSnapshot.stub(userId = fixture.userId)),
        )
        val asEmployee = fixture.appointment(
            Appointment.stub(
                initiatorUserId = other,
                businessId = businessId,
            ).copy(employee = EmployeeSnapshot.stub(userId = fixture.userId)),
        )
        val asBooker = fixture.appointment(
            Appointment.stub(
                initiatorUserId = fixture.userId,
                businessId = businessId,
            ).copy(client = ClientSnapshot.stub(userId = other)),
        )
        fixture.appointment(Appointment.stub(initiatorUserId = other, businessId = businessId))

        whenn()
        val appointments = suspendTransaction { fixture.sut.getAppointmentsOfUser(fixture.userId) }

        then()
        assertEquals(setOf(asClient, asEmployee, asBooker), appointments.map { it.id }.toSet())
    }

    @Test
    fun `should list the appointments with their services`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.business()
        val stored = Appointment.stub(initiatorUserId = fixture.userId, businessId = businessId)
        fixture.appointment(stored)

        whenn()
        val appointments = suspendTransaction { fixture.sut.getAppointmentsOfUser(fixture.userId) }

        then()
        assertEquals(stored.services.map { it.name }, appointments.single().services.map { it.name })
    }

    @Test
    fun `should list the requests where the user is the client, the employee or the one who booked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.business()
        val other = Uuid.random()
        val asClient = fixture.request(
            AppointmentRequest.stub(
                initiatorUserId = other,
                businessId = businessId,
            ).copy(client = ClientSnapshot.stub(userId = fixture.userId)),
        )
        val asEmployee = fixture.request(
            AppointmentRequest.stub(
                initiatorUserId = other,
                businessId = businessId,
                employee = EmployeeSnapshot.stub(userId = fixture.userId),
            ),
        )
        val asBooker = fixture.request(
            AppointmentRequest.stub(
                initiatorUserId = fixture.userId,
                businessId = businessId,
            ).copy(client = ClientSnapshot.stub(userId = other)),
        )
        fixture.request(AppointmentRequest.stub(initiatorUserId = other, businessId = businessId))

        whenn()
        val requests = suspendTransaction { fixture.sut.getRequestsOfUser(fixture.userId) }

        then()
        assertEquals(setOf(asClient, asEmployee, asBooker), requests.map { it.id }.toSet())
    }

    @Test
    fun `should name the businesses asked for`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val studio = fixture.business(name = "Studio")
        val barber = fixture.business(name = "Barber")
        fixture.business(name = "Unrelated")

        whenn()
        val names = suspendTransaction { fixture.sut.getBusinessNames(listOf(studio, barber)) }

        then()
        assertEquals(mapOf(studio to "Studio", barber to "Barber"), names)
    }
}
