package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.business.operation.GetBusinessById
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.BusinessDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import library.permissions.ResourcePermission
import library.schedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SeedDemoAppointmentsImplTest {

    private class SutFixture {
        val businessClient = mockk<BusinessClient>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>(relaxUnitFun = true)
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>(relaxUnitFun = true)
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val sut = SeedDemoAppointmentsImpl(
            businessClient,
            subscriptionDataSource,
            settingsDataSource,
            appointmentPermissionDataSource,
            appointmentDataSource,
            requestDataSource,
            deletedUserDataSource,
            eventProducer,
            transactionManager,
        )
        val timeZone = TimeZone.of("Europe/Kyiv")
        val business = BusinessDTO.stub(timeZone = timeZone)
        val settings = AppointmentSettings(business.id, timeZone)
        val employee = Employee.stub(
            businessId = business.id,
            userId = business.ownerUserId,
            schedule = Schedule().workingWeek(),
        )
        val services = listOf(Service.stub(businessId = business.id), Service.stub(businessId = business.id))
        val clients = List(3) { ClientRemote.stub(id = Uuid.random()) }
        val createdAppointments = mutableListOf<Appointment>()
        val createdRequests = mutableListOf<AppointmentRequest>()

        init {
            transactionManager.mockTransaction()
            coEvery { businessClient.getBusinessById(business.id) } returns Result.success(business)
            clients.forEach { client ->
                coEvery {
                    businessClient.getStaffBookingContext(business.id, employee.id, client.id, services.map { it.id })
                } returns Result.success(AppointmentBookingContext(employee, client, services))
            }
            coEvery { deletedUserDataSource.isDeletedForShare(business.ownerUserId) } returns false
            coEvery { settingsDataSource.get(business.id) } returns null
            coEvery { settingsDataSource.create(any()) } answers { firstArg() }
            coEvery { settingsDataSource.getForUpdate(business.id) } returns settings
            coEvery { appointmentDataSource.getEmployeeBookings(any(), any(), any()) } returns emptyList()
            coEvery { appointmentDataSource.create(any<Appointment>()) } answers { recordAppointment(firstArg()) }
            coEvery { requestDataSource.create(any()) } answers { recordRequest(firstArg()) }
        }

        fun recordAppointment(appointment: Appointment): Appointment {
            return appointment.also(createdAppointments::add)
        }

        fun recordRequest(request: AppointmentRequest): AppointmentRequest {
            return request.also(createdRequests::add)
        }

        suspend fun seed(): Result<Unit> {
            return sut(business.id, business.ownerUserId, employee.id, services.map { it.id }, clients.map { it.id })
        }
    }

    @Test
    fun `should enable appointments for the demo business with the owner holding every permission`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.seed()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) {
            fixture.subscriptionDataSource.attachBusiness(
                match<BusinessSnapshot> {
                    it.id == fixture.business.id &&
                        it.isEnabled
                },
            )
        }
        coVerify(exactly = 1) {
            fixture.appointmentPermissionDataSource.setPermission(
                fixture.business.ownerUserId,
                fixture.business.id,
                ResourcePermission.FULL,
                suspended = false,
            )
        }
        coVerify(exactly = 1) { fixture.settingsDataSource.create(match { it.businessId == fixture.business.id }) }
    }

    @Test
    fun `should book sample appointments only on working days within working hours`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.seed()

        then()
        val days = fixture.createdAppointments.map { it.date.toLocalDateTime(fixture.timeZone).dayOfWeek }
        assertTrue(fixture.createdAppointments.isNotEmpty())
        assertTrue(days.none { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }) { days.toString() }
        assertTrue(fixture.createdAppointments.all { it.status == AppointmentStatus.SCHEDULED })
        assertTrue(fixture.createdAppointments.all { it.employee.id == fixture.employee.id })
    }

    @Test
    fun `should spread the sample appointments over every sample client and service`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.seed()

        then()
        assertEquals(fixture.clients.map { it.id }.toSet(), fixture.createdAppointments.map { it.client.id }.toSet())
        assertEquals(
            fixture.services.map { it.id }.toSet(),
            fixture.createdAppointments.flatMap { booking -> booking.services.map { it.id } }.toSet(),
        )
    }

    @Test
    fun `should record a visit for every sample appointment so the client is kept`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.seed()

        then()
        coVerify(exactly = fixture.createdAppointments.size) {
            fixture.eventProducer.send(any<AppointmentEvent.ClientVisitBooked>(), any())
        }
    }

    @Test
    fun `should add two pending requests in the future`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val before = Clock.System.now()

        whenn()
        fixture.seed()

        then()
        assertEquals(2, fixture.createdRequests.size)
        assertTrue(fixture.createdRequests.all { it.status == AppointmentRequestStatus.PENDING && it.date > before })
    }

    @Test
    fun `should skip every slot the employee is already busy at`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.appointmentDataSource.getEmployeeBookings(any(), any(), any()) } returns
            listOf(BookedRange.stub(start = Instant.DISTANT_PAST, end = Instant.DISTANT_FUTURE))

        whenn()
        val result = fixture.seed()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertTrue(fixture.createdAppointments.isEmpty())
        assertTrue(fixture.createdRequests.isEmpty())
    }

    @Test
    fun `should seed nothing again once the business has appointment settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settingsDataSource.get(fixture.business.id) } returns fixture.settings

        whenn()
        val result = fixture.seed()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.subscriptionDataSource.attachBusiness(any()) }
        assertTrue(fixture.createdAppointments.isEmpty())
    }

    @Test
    fun `should seed nothing for an owner deleted in the meantime`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deletedUserDataSource.isDeletedForShare(fixture.business.ownerUserId) } returns true

        whenn()
        val result = fixture.seed()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.settingsDataSource.create(any()) }
    }

    @Test
    fun `should seed nothing for a business deleted in the meantime`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessClient.getBusinessById(fixture.business.id) } returns
            Result.failure(GetBusinessById.Error.NotFound())

        whenn()
        val result = fixture.seed()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should fail so the event is retried when the booking context can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("business unreachable")
        coEvery { fixture.businessClient.getStaffBookingContext(any(), any(), any(), any()) } returns
            Result.failure(error)

        whenn()
        val result = fixture.seed()

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should do nothing for a seed without services or clients`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.business.id, Uuid.random(), Uuid.random(), emptyList(), emptyList())

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.businessClient.getBusinessById(any()) }
    }
}
