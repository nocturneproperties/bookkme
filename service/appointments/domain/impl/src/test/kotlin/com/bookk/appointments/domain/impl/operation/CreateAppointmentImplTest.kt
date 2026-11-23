package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatusError
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.BookingConflictError
import com.bookk.appointments.domain.api.entity.BookingScheduleError
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import library.schedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CreateAppointmentImplTest {

    private val futureDate = Instant.parse("2099-01-01T00:00:00Z")

    private fun rescheduleContext(
        request: AppointmentRequest,
        schedule: Schedule = Schedule(),
    ): AppointmentRescheduleContext {
        val employee = Employee.stub(id = request.employee.id, businessId = request.businessId, schedule = schedule)
        return AppointmentRescheduleContext.stub(employee = employee, services = emptyList())
    }

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource> {
            coEvery { getEmployeeBookings(any(), any(), any()) } returns emptyList()
        }
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val businessClient = mockk<BusinessClient>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>()

        val sut = CreateAppointmentImpl(
            appointmentDataSource,
            requestDataSource,
            settingsDataSource,
            appointmentPermissionDataSource,
            subscriptionDataSource,
            businessClient,
            transactionManager,
            eventProducer,
        )
    }

    // createBypassingPermissions(request, employeeSchedule = Schedule()) - called by CreateAppointmentRequestImpl when
    // automaticApproval is on, so the caller is the client, not business staff - no permission check applies.

    @Test
    fun `should create appointment successfully`() = runUnitTest {
        given()

        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(
            initiatorUserId = userId,
            businessId = request.businessId,
            date = request.date,
        )
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val sutFixture = SutFixture()

        with(sutFixture) {
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.isSuccess)
        assertEquals(appointment, result.getOrNull())
    }

    @Test
    fun `should create appointment successfully when caller has no business permissions`() = runUnitTest {
        given()

        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(
            initiatorUserId = userId,
            businessId = request.businessId,
            date = request.date,
        )
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val sutFixture = SutFixture()

        with(sutFixture) {
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { sutFixture.appointmentPermissionDataSource.getPermission(any(), any()) }
    }

    @Test
    fun `should return failure when date is not in workday`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(
            initiatorUserId = userId,
            businessId = request.businessId,
            date = request.date,
        )
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val sutFixture = SutFixture()

        with(sutFixture) {
            every { settings.requireBookable(request.date, request.dateEnd, any()) } throws
                BookingScheduleError.BusinessDateNotAllowed()
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BookingScheduleError.BusinessDateNotAllowed)
    }

    @Test
    fun `should return failure when request date is in the past`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val pastDate = Instant.parse("2000-01-01T00:00:00Z")
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = pastDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val sutFixture = SutFixture()

        with(sutFixture) {
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateAppointment.Error.DateInThePastNotAllowed)
    }

    @Test
    fun `should return failure when time is not in worktime`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val appointment = Appointment.stub(
            initiatorUserId = userId,
            businessId = request.businessId,
            date = request.date,
        )
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val sutFixture = SutFixture()

        with(sutFixture) {
            every { settings.requireBookable(request.date, request.dateEnd, any()) } throws
                BookingScheduleError.BusinessTimeNotAllowed()
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(request.businessId) } returns mockk(relaxed = true)
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = sutFixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BookingScheduleError.BusinessTimeNotAllowed)
    }

    @Test
    fun `should return failure when create event fails to be sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            every { settings.requireBookable(appointment.date, appointment.dateEnd, any()) } just Runs
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } answers { throw RuntimeException("Producer fail") }
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

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
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            every { settings.requireBookable(appointment.date, appointment.dateEnd, any()) } just Runs
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

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
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val businessSnapshot = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val eventSlot = slot<AppointmentEvent.RequestApproved>()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            every { settings.requireBookable(appointment.date, appointment.dateEnd, any()) } just Runs
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(capture(eventSlot), any()) } returns Unit
            coEvery { eventProducer.send(any<AppointmentEvent.ClientVisitBooked>(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

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
        val request = AppointmentRequest.stub(
            initiatorUserId = bookingUserId,
            businessId = businessId,
            date = futureDate,
        )
            .let { it.copy(client = it.client.copy(userId = clientUserId)) }
        val appointment = Appointment.stub(initiatorUserId = bookingUserId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val eventSlot = slot<AppointmentEvent.RequestApproved>()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            every { settings.requireBookable(appointment.date, appointment.dateEnd, any()) } just Runs
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns BusinessSnapshot.stub()
            coEvery { eventProducer.send(capture(eventSlot), any()) } returns Unit
            coEvery { eventProducer.send(any<AppointmentEvent.ClientVisitBooked>(), any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

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
        val appointment = Appointment.stub(
            initiatorUserId = userId,
            businessId = request.businessId,
            date = request.date,
        )
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
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
    fun `should return failure when approving with READ permissions and not the assigned employee`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = true, update = false, delete = false)
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
        val appointment = Appointment.stub(
            initiatorUserId = request.initiatorUserId,
            businessId = request.businessId,
            date = request.date,
        )
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = true, update = false, delete = false)
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
            coEvery { requestDataSource.get(requestId) } returns null
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
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
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
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
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
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
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
    fun `should not ask the business service about the employee when the caller has no access`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission.NONE
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke(userId, request.id)

        then()
        coVerify(exactly = 0) { fixture.requestDataSource.getForUpdate(any()) }
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any(), any()) }
    }

    @Test
    fun `should lock the business settings before reading the permission when approving`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
            coEvery { appointmentDataSource.create(request) } returns
                Appointment.stub(businessId = request.businessId, date = request.date)
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
            fixture.appointmentDataSource.getEmployeeBookings(request.businessId, request.employee.id, any())
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
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns mockk()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission.NONE
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should tell the business the client has a visit until the approved appointment ends`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val appointment = Appointment.stub(initiatorUserId = userId, businessId = businessId, date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val businessSnapshot = BusinessSnapshot.stub()

        with(fixture) {
            coEvery { settingsDataSource.getForUpdate(businessId) } returns settings
            coEvery { settings.automaticApproval } returns false
            every { settings.requireBookable(appointment.date, appointment.dateEnd, any()) } just Runs
            coEvery { appointmentDataSource.create(request) } returns appointment
            coEvery { requestDataSource.approve(request) } returns Unit
            coEvery { subscriptionDataSource.getBusinessSnapshot(businessId) } returns businessSnapshot
            coEvery { eventProducer.send(any(), any()) } returns Unit
            transactionManager.mockTransaction()
        }
        whenn()
        val result = fixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<AppointmentEvent.ClientVisitBooked> {
                    it.businessId == businessId &&
                        it.clientId == request.client.id &&
                        it.visitEnd == request.dateEnd
                },
                any(),
            )
        }
    }

    @Test
    fun `should check the slot against the given employee schedule when booking without permissions`() =
        runUnitTest {
            given()
            val request = AppointmentRequest.stub(date = futureDate)
            val employeeSchedule = Schedule.empty()
            val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
            val fixture = SutFixture()

            with(fixture) {
                coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
                every { settings.requireBookable(request.date, request.dateEnd, employeeSchedule) } throws
                    BookingScheduleError.EmployeeTimeNotAllowed()
                transactionManager.mockTransaction()
            }

            whenn()
            val result = fixture.sut.createBypassingPermissions(request, employeeSchedule)

            then()
            assertTrue(result.exceptionOrNull() is BookingScheduleError.EmployeeTimeNotAllowed)
            verify(exactly = 1) { settings.requireBookable(request.date, request.dateEnd, employeeSchedule) }
        }

    @Test
    fun `should check the approved slot against the assigned employee's current schedule`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val employeeSchedule = Schedule.empty()
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request, employeeSchedule))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            every { settings.requireBookable(request.date, any(), employeeSchedule) } throws
                BookingScheduleError.EmployeeDateNotAllowed()
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is BookingScheduleError.EmployeeDateNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.create(any<AppointmentRequest>()) }
    }

    @Test
    fun `should not approve a request whose employee is suspended`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.failure(GetAppointmentRescheduleContext.Error.EmployeeSuspended())
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentRescheduleContext.Error.EmployeeSuspended)
        coVerify(exactly = 0) { fixture.requestDataSource.getForUpdate(any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.create(any<AppointmentRequest>()) }
    }

    @Test
    fun `should re-read the request with a row lock before approving it`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val fixture = SutFixture()

        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns
                request.copy(status = AppointmentRequestStatus.DECLINED)
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyDeclined)
        coVerifyOrder {
            fixture.requestDataSource.get(request.id)
            fixture.businessClient.getAppointmentRescheduleContext(
                request.businessId,
                request.employee.id,
                emptyList(),
                false,
            )
            fixture.requestDataSource.getForUpdate(request.id)
            fixture.settingsDataSource.getForUpdate(request.businessId)
        }
    }

    @Test
    fun `should not approve a request when the employee has another booking at that time`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId, date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 0 }
        val fixture = SutFixture()
        with(fixture) {
            coEvery { requestDataSource.get(request.id) } returns request
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
            coEvery {
                businessClient.getAppointmentRescheduleContext(
                    request.businessId,
                    request.employee.id,
                    emptyList(),
                    false,
                )
            } returns Result.success(rescheduleContext(request))
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { appointmentPermissionDataSource.getPermission(userId, request.businessId) } returns
                ResourcePermission(view = false, update = true, delete = false)
            coEvery {
                appointmentDataSource.getEmployeeBookings(request.businessId, request.employee.id, any())
            } returns listOf(BookedRange.stub(start = request.date, end = request.dateEnd))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is BookingConflictError.EmployeeBusy)
        coVerify(exactly = 0) { fixture.appointmentDataSource.create(any<AppointmentRequest>()) }
        coVerify(exactly = 0) { fixture.requestDataSource.approve(any()) }
    }

    @Test
    fun `should keep the break after the employee's previous booking when approving`() = runUnitTest {
        given()
        val request = AppointmentRequest.stub(date = futureDate)
        val settings = mockk<AppointmentSettings> { every { inBetweenBreakInMinutes } returns 10 }
        val fixture = SutFixture()
        with(fixture) {
            every { settings.requireBookable(request.date, request.dateEnd, any()) } just Runs
            coEvery { settingsDataSource.getForUpdate(request.businessId) } returns settings
            coEvery {
                appointmentDataSource.getEmployeeBookings(
                    request.businessId,
                    request.employee.id,
                    request.date - 10.minutes..<request.dateEnd + 10.minutes,
                )
            } returns listOf(BookedRange.stub(start = request.date - 1.hours, end = request.date - 5.minutes))
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.createBypassingPermissions(request, employeeSchedule = Schedule())

        then()
        assertTrue(result.exceptionOrNull() is BookingConflictError.EmployeeBusy)
    }
}
