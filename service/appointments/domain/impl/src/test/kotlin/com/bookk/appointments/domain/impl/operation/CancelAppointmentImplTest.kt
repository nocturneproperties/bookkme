package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CancelAppointmentImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()

        val sut = CancelAppointmentImpl(
            appointmentDataSource,
            appointmentPermissionDataSource,
            subscriptionDataSource,
            eventProducer,
            transactionManager
        )
    }

    private val testUserId = Uuid.random()
    private val testBusinessId = Uuid.random()
    private val testCancellation = AppointmentCancellation(
        id = Uuid.random(),
        reason = "User cancelled"
    )

    @Test
    fun `should cancel appointment successfully`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.SCHEDULED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment
        coEvery { fixture.appointmentDataSource.cancel(testCancellation.id, testCancellation.reason) } returns appointment.copy(status = AppointmentStatus.CANCELLED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when appointment already cancelled`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.CANCELLED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.AlreadyCancelled)
    }

    @Test
    fun `should return failure when appointment already completed`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.COMPLETED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.AlreadyCompleted)
    }

    @Test
    fun `should return failure when appointment is marked as no-show`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.NO_SHOW)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.MarkedNoShow)
    }

    @Test
    fun `should return failure when user has read permission but appointment belongs to another employee`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.SCHEDULED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = true, update = false, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.bookk.core.domain.entity.Error.OperationNotAllowed)
    }

    @Test
    fun `should cancel own appointment successfully with read permission`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.SCHEDULED, employee = EmployeeSnapshot.stub(userId = testUserId))

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = true, update = false, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment
        coEvery { fixture.appointmentDataSource.cancel(testCancellation.id, testCancellation.reason) } returns appointment.copy(status = AppointmentStatus.CANCELLED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should send appointment cancelled event successfully`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.SCHEDULED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment
        coEvery { fixture.appointmentDataSource.cancel(testCancellation.id, testCancellation.reason) } returns appointment.copy(status = AppointmentStatus.CANCELLED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns mockk(relaxed = true)

        whenn()
        fixture.sut.invoke(testUserId, testCancellation)

        then()
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.Cancelled::class), any()) }
    }

    @Test
    fun `should propagate business time zone onto the published event`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.SCHEDULED)
        val businessSnapshot = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val eventSlot = slot<AppointmentEvent.Cancelled>()

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment
        coEvery { fixture.appointmentDataSource.cancel(testCancellation.id, testCancellation.reason) } returns appointment.copy(status = AppointmentStatus.CANCELLED)
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(testBusinessId) } returns businessSnapshot
        coEvery { fixture.eventProducer.send(capture(eventSlot), any()) } returns Unit

        whenn()
        fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertEquals(TimeZone.of("Europe/Kyiv"), eventSlot.captured.timeZone)
    }

    @Test
    fun `should check permission against the stored appointment business`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointmentBusinessId = Uuid.random()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = appointmentBusinessId)

        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment
        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, appointmentBusinessId) } returns ResourcePermission.NONE

        whenn()
        val result = fixture.sut.invoke(testUserId, testCancellation)

        then()
        assertTrue(result.exceptionOrNull() is com.bookk.core.domain.entity.Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.cancel(any(), any()) }
    }

    @Test
    fun `should not cancel an appointment that is not scheduled`() = runUnitTest {
        val fixture = SutFixture()
        given()
        fixture.transactionManager.mockTransaction()
        val appointment = Appointment.stub(id = testCancellation.id, businessId = testBusinessId)
            .copy(status = AppointmentStatus.COMPLETED)

        coEvery { fixture.appointmentPermissionDataSource.getPermission(testUserId, testBusinessId) } returns ResourcePermission(view = false, update = true, delete = false)
        coEvery { fixture.appointmentDataSource.getForUpdate(testCancellation.id) } returns appointment

        whenn()
        fixture.sut.invoke(testUserId, testCancellation)

        then()
        coVerify(exactly = 0) { fixture.appointmentDataSource.cancel(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(AppointmentEvent.Cancelled::class), any()) }
    }
}
