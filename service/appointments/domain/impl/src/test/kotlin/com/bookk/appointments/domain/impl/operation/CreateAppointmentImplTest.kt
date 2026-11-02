package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatusError
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CreateAppointmentImplTest {

    private val futureDate = Instant.parse("2099-01-01T00:00:00Z")

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>()

        val sut = CreateAppointmentImpl(
            appointmentDataSource,
            requestDataSource,
            settingsDataSource,
            appointmentPermissionDataSource,
            subscriptionDataSource,
            transactionManager,
            eventProducer,
        )
    }

    // createBypassingPermissions(request) - called by CreateAppointmentRequestImpl when
    // automaticApproval is on, so the caller is the client, not business staff - no permission check applies.

    @Test
    fun `should create appointment successfully`() = runUnitTest {
        given()

        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns true
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isSuccess)
        assertEquals(appointment, result.getOrNull())
    }

    @Test
    fun `should create appointment successfully when caller has no business permissions`() = runUnitTest {
        given()

        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns true
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { sutFixture.appointmentPermissionDataSource.getPermission(any(), any()) }
    }

    @Test
    fun `should return failure when request overlaps with existing appointment`() = runUnitTest {
        given()

        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns true
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns true
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointment.Error.AppointmentForThisTimeExists)
    }

    @Test
    fun `should return failure when date is not in workday`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settings.isInWorkday(request.date) } returns false
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns false
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns true
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointment.Error.RequestForThisDateNotAllowed)
    }

    @Test
    fun `should return failure when request date is in the past`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val pastDate = Instant.parse("2000-01-01T00:00:00Z")
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = pastDate)
        val settings = mockk<AppointmentSettings>()
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointment.Error.DateInThePastNotAllowed)
    }

    @Test
    fun `should return failure when time is not in worktime`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns false
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns true
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointment.Error.RequestForThisTimeNotAllowed)
    }

    @Test
    fun `should return failure when create event fails to be sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(appointment.date) } returns true
            coEvery { settings.isInWorktime(appointment.date, appointment.dateEnd) } returns true
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } answers { throw RuntimeException("Producer fail") }
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Producer fail", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should send request approved event if appointment created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(appointment.date) } returns true
            coEvery { settings.isInWorktime(appointment.date, appointment.dateEnd) } returns true
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.RequestApproved::class), any()) }
    }

    @Test
    fun `should propagate business time zone onto the published event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings>()
        val businessSnapshot = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val eventSlot = slot<AppointmentEvent.RequestApproved>()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            coEvery { settings.isInWorkday(appointment.date) } returns true
            coEvery { settings.isInWorktime(appointment.date, appointment.dateEnd) } returns true
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(capture(eventSlot), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isSuccess)
        assertEquals(TimeZone.of("Europe/Kyiv"), eventSlot.captured.timeZone)
    }

    @Test
    fun `should address the approval to the client's own account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val approverId = Uuid.random()
        val bookingUserId = Uuid.random()
        val businessId = Uuid.random()
        val clientUserId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = bookingUserId, businessId = businessId, date = futureDate)
            .let { it.copy(client = it.client.copy(userId = clientUserId)) }
        val appointment = Appointment.stub(initiatorUserId = bookingUserId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings>()
        val eventSlot = slot<AppointmentEvent.RequestApproved>()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.isInWorkday(appointment.date) } returns true
            coEvery { settings.isInWorktime(appointment.date, appointment.dateEnd) } returns true
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns BusinessSnapshot.stub()
            coEvery { eventProducer.send(capture(eventSlot), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.createBypassingPermissions(request)

        then()
        assertTrue(result.isSuccess)
        assertEquals(clientUserId, eventSlot.captured.clientUserId)
    }

    // invoke(userId, appointmentRequestId: Uuid) - staff approving a pending request someone else
    // submitted, so this is the one call path that still requires a business-permission check.

    @Test
    fun `should create appointment from request id successfully`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns true
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.isSuccess)
        assertEquals(appointment, result.getOrNull())
    }

    @Test
    fun `should return failure when approving request with READ permissions and not the assigned employee`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should approve own request successfully with read permission`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
            .copy(employee = EmployeeSnapshot.stub(userId = userId))
        val appointment = Appointment.stub(initiatorUserId = request.initiatorUserId, businessId = request.businessId, date = request.date)
        val settings = mockk<AppointmentSettings>()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns true
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return NotFound when appointment request id does not exist`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val requestId = Uuid.random()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(requestId) } returns null
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, requestId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should not approve a declined request`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate).copy(status = AppointmentRequestStatus.DECLINED)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = false, update = true, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyDeclined)
        coVerify(exactly = 0) { fixture.appointmentDataSource.create(any<AppointmentRequest>()) }
        coVerify(exactly = 0) { fixture.requestDataSource.approve(any()) }
    }

    @Test
    fun `should not approve a cancelled request`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate).copy(status = AppointmentRequestStatus.CANCELLED)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = false, update = true, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyDeclined)
        coVerify(exactly = 0) { fixture.appointmentDataSource.create(any<AppointmentRequest>()) }
        coVerify(exactly = 0) { fixture.requestDataSource.approve(any()) }
    }

    @Test
    fun `should not approve an already approved request`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate).copy(status = AppointmentRequestStatus.APPROVED)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = false, update = true, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyApproved)
        coVerify(exactly = 0) { fixture.appointmentDataSource.create(any<AppointmentRequest>()) }
        coVerify(exactly = 0) { fixture.requestDataSource.approve(any()) }
    }

    @Test
    fun `should read the request with a row lock when approving`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission.NONE
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(userId, request.id)

        then()
        coVerify(exactly = 1) { fixture.requestDataSource.getForUpdate(request.id) }
        coVerify(exactly = 0) { fixture.requestDataSource.get(any()) }
    }

    @Test
    fun `should lock the business settings before reading the permission when approving`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val settings = mockk<AppointmentSettings>()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { settings.isInWorkday(request.date) } returns true
            coEvery { settings.isInWorktime(request.date, request.dateEnd) } returns true
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { appointmentDataSource.hasOverlapsWith(request) } returns false
            coEvery { appointmentDataSource.create(request) } returns Appointment.stub(businessId = request.businessId, date = request.date)
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.requestDataSource.getForUpdate(request.id)
            fixture.settingsDataSource.getForUpdate(request.businessId)
            fixture.appointmentPermissionDataSource.getPermission(userId, request.businessId)
            fixture.appointmentDataSource.hasOverlapsWith(request)
        }
        coVerify(exactly = 1) { fixture.settingsDataSource.getForUpdate(request.businessId) }
    }

    @Test
    fun `should not reveal the request's status to a caller without access`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate).copy(status = AppointmentRequestStatus.APPROVED)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns ResourcePermission.NONE
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }
}
