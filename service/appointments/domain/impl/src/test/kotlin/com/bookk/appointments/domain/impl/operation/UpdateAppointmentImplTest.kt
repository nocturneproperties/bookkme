package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.entity.AppointmentUpdate
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.UpdateAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateAppointmentImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val businessClient = mockk<BusinessClient>()
        val transactionManager = mockk<TransactionManager>()
        val settings = mockk<AppointmentSettings>()
        val eventProducer = mockk<StandardEventProducer>()
        val sentEvents = mutableListOf<AppointmentEvent>()
        val sentInTransaction = mutableListOf<Boolean>()

        val sut = UpdateAppointmentImpl(
            appointmentDataSource,
            settingsDataSource,
            appointmentPermissionDataSource,
            businessClient,
            transactionManager,
            eventProducer,
        )

        fun givenValidUpdate(
            userId: Uuid,
            stored: Appointment,
            permission: ResourcePermission = ResourcePermission(view = false, update = true, delete = false),
        ) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(stored.id) } returns stored
            coEvery { appointmentDataSource.getForUpdate(stored.id) } returns stored
            coEvery { settingsDataSource.getForUpdate(stored.businessId) } returns settings
            coEvery { settings.isInWorkday(any()) } returns true
            coEvery { settings.isInWorktime(any(), any()) } returns true
            coEvery { appointmentPermissionDataSource.getPermission(userId, stored.businessId) } returns permission
            coEvery { appointmentDataSource.hasOverlapsWith(any<Appointment>()) } returns false
            coEvery { appointmentDataSource.reopen(stored.id) } just runs
            coEvery { appointmentDataSource.update(any<Appointment>()) } answers { firstArg() }
            coEvery { eventProducer.send(any<AppointmentEvent>(), any()) } coAnswers {
                sentEvents += firstArg<AppointmentEvent>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    private val futureDate = Instant.parse("2099-01-01T00:00:00Z")
    private val laterDate = Instant.parse("2099-01-02T00:00:00Z")

    private fun keepingEverything(stored: Appointment, date: Instant = laterDate) = AppointmentUpdate(
        id = stored.id,
        date = date,
        note = "Updated note",
        employeeId = stored.employee.id,
        services = stored.services.map { RequestedService(serviceId = it.id, count = 1) },
    )

    @Test
    fun `should reschedule keeping the stored employee, services, client and business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored)

        whenn()
        val result = fixture.sut(userId, update)

        then()
        val expected = stored.copy(date = laterDate, note = "Updated note")
        assertEquals(expected, result.getOrNull())
        coVerify(exactly = 1) { fixture.appointmentDataSource.update(expected) }
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any()) }
    }

    @Test
    fun `should return the stored appointment rather than the request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        val persisted = stored.copy(note = "Persisted note")
        fixture.givenValidUpdate(userId, stored)
        coEvery { fixture.appointmentDataSource.update(any<Appointment>()) } returns persisted

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertEquals(persisted, result.getOrNull())
    }

    @Test
    fun `should keep the stored price of a service already on the appointment and repeat it per count`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val storedService = ServiceSnapshot.stub().copy(price = Money.parse("USD 40"))
        val stored = Appointment.stub(date = futureDate).copy(services = listOf(storedService))
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored).copy(services = listOf(RequestedService(serviceId = storedService.id, count = 2)))

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(listOf(storedService, storedService), result.getOrNull()?.services)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any()) }
    }

    @Test
    fun `should resolve only newly added services from the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        val storedService = stored.services.single()
        val added = Service.stub(businessId = stored.businessId, price = Money.parse("USD 25"))
        val update = keepingEverything(stored).copy(
            services = listOf(RequestedService(serviceId = storedService.id, count = 1), RequestedService(serviceId = added.id, count = 1)),
        )
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery {
                businessClient.getAppointmentRescheduleContext(stored.businessId, null, listOf(added.id))
            } returns Result.success(AppointmentRescheduleContext(employee = null, services = listOf(added)))
        }

        whenn()
        val result = fixture.sut(userId, update)

        then()
        val addedSnapshot = ServiceSnapshot(id = added.id, name = added.name, groupId = added.group.id, price = added.price, duration = added.duration)
        assertEquals(listOf(storedService, addedSnapshot), result.getOrNull()?.services)
    }

    @Test
    fun `should resolve a newly assigned employee from the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        val newEmployee = Employee.stub(businessId = stored.businessId, name = "Ann", lastName = "Lee")
        val update = keepingEverything(stored).copy(employeeId = newEmployee.id)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery {
                businessClient.getAppointmentRescheduleContext(stored.businessId, newEmployee.id, emptyList())
            } returns Result.success(AppointmentRescheduleContext(employee = newEmployee, services = emptyList()))
        }

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(EmployeeSnapshot(id = newEmployee.id, userId = newEmployee.userId, fullName = "Ann Lee"), result.getOrNull()?.employee)
    }

    @Test
    fun `should not write when the business cannot resolve the change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        val update = keepingEverything(stored).copy(employeeId = Uuid.random())
        val employeeNotFound = Error.NotFound()
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { businessClient.getAppointmentRescheduleContext(stored.businessId, update.employeeId, emptyList()) } returns Result.failure(employeeNotFound)
        }

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(employeeNotFound, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should take the settings lock only after resolving the change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        val update = keepingEverything(stored).copy(employeeId = Uuid.random())
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { businessClient.getAppointmentRescheduleContext(any(), any(), any()) } returns Result.failure(Error.NotFound())
        }

        whenn()
        fixture.sut(userId, update)

        then()
        coVerify(exactly = 0) { fixture.settingsDataSource.getForUpdate(any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.getForUpdate(any()) }
    }

    @Test
    fun `should reject an empty service selection`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(Uuid.random(), AppointmentUpdate.stub(services = emptyList()))

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.InvalidServiceSelection)
        coVerify(exactly = 0) { fixture.appointmentDataSource.getForUpdate(any()) }
    }

    @Test
    fun `should reject a service selection with a non-positive count`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(Uuid.random(), AppointmentUpdate.stub(services = listOf(RequestedService.stub(count = 0))))

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.InvalidServiceSelection)
    }

    @Test
    fun `should reject a service selection listing the same service twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val serviceId = Uuid.random()

        whenn()
        val result = fixture.sut(
            Uuid.random(),
            AppointmentUpdate.stub(services = listOf(RequestedService.stub(serviceId = serviceId), RequestedService.stub(serviceId = serviceId))),
        )

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.InvalidServiceSelection)
    }

    @Test
    fun `should return failure when appointment does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val update = AppointmentUpdate.stub(date = futureDate)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(update.id) } throws Error.NotFound()
        }

        whenn()
        val result = fixture.sut(Uuid.random(), update)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should return failure when business settings do not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { settingsDataSource.getForUpdate(stored.businessId) } returns null
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should lock the settings and then the appointment before reading the permission in the booking transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored)

        whenn()
        fixture.sut(userId, keepingEverything(stored))

        then()
        coVerifyOrder {
            fixture.appointmentDataSource.get(stored.id)
            fixture.appointmentPermissionDataSource.getPermission(userId, stored.businessId)
            fixture.settingsDataSource.getForUpdate(stored.businessId)
            fixture.appointmentDataSource.getForUpdate(stored.id)
            fixture.appointmentPermissionDataSource.getPermission(userId, stored.businessId)
            fixture.appointmentDataSource.hasOverlapsWith(any<Appointment>())
            fixture.appointmentDataSource.update(any<Appointment>())
        }
    }

    @Test
    fun `should return a conflict when the appointment changed while the change was resolved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { appointmentDataSource.getForUpdate(stored.id) } returns stored.copy(note = "Changed meanwhile")
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is Error.TransactionConflict)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should check the permission again after taking the locks`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { appointmentPermissionDataSource.getPermission(userId, stored.businessId) } returnsMany listOf(
                ResourcePermission(view = false, update = true, delete = false),
                ResourcePermission.NONE,
            )
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should check permission against the stored appointment business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored, permission = ResourcePermission.NONE)

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored).copy(employeeId = Uuid.random()))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should return failure when user has view permission but appointment belongs to another employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored, permission = ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should update own appointment with view permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate).copy(employee = EmployeeSnapshot.stub(userId = userId))
        fixture.givenValidUpdate(userId, stored, permission = ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when caller is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { appointmentPermissionDataSource.getPermission(userId, stored.businessId) } throws EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
    }

    @Test
    fun `should return failure when appointment is cancelled`() = runUnitTest {
        given()
        assertRejectsStoredStatus(AppointmentStatus.CANCELLED) { it is AppointmentStatusError.AlreadyCancelled }
    }

    @Test
    fun `should reschedule a completed appointment within its edit window`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 1.days).copy(status = AppointmentStatus.COMPLETED)
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored)

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(stored.copy(date = laterDate, note = "Updated note", status = AppointmentStatus.SCHEDULED, completedBy = null, priceAdjustment = null), result.getOrNull())
        coVerify(exactly = 1) { fixture.appointmentDataSource.hasOverlapsWith(any()) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.ClientVisitBooked::class), any()) }
    }

    @Test
    fun `should reschedule a no-show appointment within its edit window`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 1.days).copy(status = AppointmentStatus.NO_SHOW)
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored)

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(stored.copy(date = laterDate, note = "Updated note", status = AppointmentStatus.SCHEDULED, completedBy = null, priceAdjustment = null), result.getOrNull())
        coVerify(exactly = 1) { fixture.appointmentDataSource.hasOverlapsWith(any()) }
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.ClientVisitBooked::class), any()) }
    }

    @Test
    fun `should return failure when date is in the past`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored)

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored, date = Instant.fromEpochMilliseconds(0)))

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.DateInThePastNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should return failure when workday not allowed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { settings.isInWorkday(any()) } returns false
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.RequestForThisDateNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should return failure when time not allowed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { settings.isInWorktime(any(), any()) } returns false
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.RequestForThisTimeNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should check overlaps against the rescheduled appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        with(fixture) {
            givenValidUpdate(userId, stored)
            coEvery { appointmentDataSource.hasOverlapsWith(stored.copy(date = laterDate, note = "Updated note")) } returns true
        }

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.AppointmentForThisTimeExists)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    private suspend fun assertRejectsStoredStatus(status: AppointmentStatus, isExpected: (Throwable?) -> Boolean) {
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate).copy(status = status)
        fixture.givenValidUpdate(userId, stored)

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored).copy(employeeId = Uuid.random()))

        then()
        assertTrue(isExpected(result.exceptionOrNull()))
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should return failure when the appointment ended more than a week ago`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 30.days)
        fixture.givenValidUpdate(userId, stored)

        whenn()
        val result = fixture.sut(userId, keepingEverything(stored))

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.EditWindowClosed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.update(any()) }
    }

    @Test
    fun `should tell the business the client has a visit until the rescheduled appointment ends`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored)

        whenn()
        val rescheduled = fixture.sut(userId, keepingEverything(stored)).getOrThrow()

        then()
        val visit = fixture.sentEvents.filterIsInstance<AppointmentEvent.ClientVisitBooked>().single()
        assertEquals(stored.businessId, visit.businessId)
        assertEquals(stored.client.id, visit.clientId)
        assertEquals(rescheduled.dateEnd, visit.visitEnd)
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should reopen a system completed appointment when rescheduling within its edit window`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 1.days).copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.SYSTEM,
            priceAdjustment = PriceAdjustment.stub(),
        )
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored)

        whenn()
        val result = fixture.sut(userId, update)

        then()
        val expected = stored.copy(date = laterDate, note = "Updated note", status = AppointmentStatus.SCHEDULED, completedBy = null, priceAdjustment = null)
        assertEquals(expected, result.getOrNull())
        coVerifyOrder {
            fixture.appointmentDataSource.reopen(stored.id)
            fixture.appointmentDataSource.update(expected)
        }
        coVerify(exactly = 1) { fixture.eventProducer.send(any(AppointmentEvent.ClientVisitBooked::class), any()) }
    }

    @Test
    fun `should reject rescheduling an unresolved appointment`() = runUnitTest {
        given()
        assertRejectsStoredStatus(AppointmentStatus.UNRESOLVED) { it is AppointmentStatusError.EditWindowClosed }
    }

    @Test
    fun `should preserve a completed outcome when editing its note`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 1.days).copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.USER,
            priceAdjustment = PriceAdjustment.stub(),
        )
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored, date = stored.date)

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(stored.copy(note = "Updated note"), result.getOrNull())
        coVerify(exactly = 0) { fixture.appointmentDataSource.hasOverlapsWith(any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.reopen(any()) }
    }

    @Test
    fun `should preserve a no-show outcome when editing its note`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 1.days).copy(
            status = AppointmentStatus.NO_SHOW,
            completedBy = null,
            priceAdjustment = PriceAdjustment.stub(),
        )
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored, date = stored.date)

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(stored.copy(note = "Updated note"), result.getOrNull())
        coVerify(exactly = 0) { fixture.appointmentDataSource.hasOverlapsWith(any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.reopen(any()) }
    }

    @Test
    fun `should not recheck availability when correcting the services of a completed appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = Clock.System.now() - 1.days).copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.USER,
        )
        fixture.givenValidUpdate(userId, stored)
        val update = keepingEverything(stored, date = stored.date).copy(
            services = stored.services.map { RequestedService(serviceId = it.id, count = 2) },
        )

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertEquals(AppointmentStatus.COMPLETED, result.getOrNull()?.status)
        assertEquals(stored.services.size * 2, result.getOrNull()?.services?.size)
        coVerify(exactly = 0) { fixture.settings.isInWorkday(any()) }
        coVerify(exactly = 0) { fixture.settings.isInWorktime(any(), any()) }
        coVerify(exactly = 0) { fixture.appointmentDataSource.hasOverlapsWith(any()) }
    }

    @Test
    fun `should recheck availability when the services of a scheduled appointment change in place`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val stored = Appointment.stub(date = futureDate)
        fixture.givenValidUpdate(userId, stored)
        coEvery { fixture.appointmentDataSource.hasOverlapsWith(any<Appointment>()) } returns true
        val update = keepingEverything(stored, date = stored.date).copy(
            services = stored.services.map { RequestedService(serviceId = it.id, count = 2) },
        )

        whenn()
        val result = fixture.sut(userId, update)

        then()
        assertTrue(result.exceptionOrNull() is UpdateAppointment.Error.AppointmentForThisTimeExists)
    }
}
