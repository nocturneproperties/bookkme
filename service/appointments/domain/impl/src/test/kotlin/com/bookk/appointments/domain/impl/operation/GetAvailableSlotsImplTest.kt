package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.DaySlots
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.TimeSlot
import com.bookk.appointments.domain.api.operation.GetAvailableSlots
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.BusinessClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAvailableSlotsImplTest {

    private val monday = LocalDate(2099, 6, 1)
    private val friday = LocalDate(2099, 6, 5)
    private val saturday = LocalDate(2099, 6, 6)
    private val kyiv = TimeZone.of("Europe/Kyiv")

    private class SutFixture {
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val businessClient = mockk<BusinessClient>()
        val transactionManager = mockk<TransactionManager>()
        val clock = TestClock(Instant.parse("2050-01-01T00:00:00Z"))
        val sut = GetAvailableSlotsImpl(
            settingsDataSource,
            subscriptionDataSource,
            appointmentDataSource,
            businessClient,
            transactionManager,
            clock,
        )
        val businessId: Uuid = Uuid.random()
        val employeeId: Uuid = Uuid.random()
        val service = Service.stub(businessId = businessId, duration = 30.minutes)

        fun givenBusiness(
            settings: AppointmentSettings,
            employeeSchedule: Schedule = settings.schedule,
            appointments: List<BookedRange> = emptyList(),
        ) {
            transactionManager.mockTransaction()
            coEvery { subscriptionDataSource.isBusinessEnabled(businessId) } returns true
            coEvery { settingsDataSource.get(businessId) } returns settings
            coEvery {
                businessClient.getAppointmentRescheduleContext(businessId, employeeId, listOf(service.id), false)
            } returns Result.success(
                AppointmentRescheduleContext(
                    employee = Employee.stub(id = employeeId, businessId = businessId, schedule = employeeSchedule),
                    services = listOf(service),
                ),
            )
            coEvery { appointmentDataSource.getEmployeeBookings(businessId, employeeId, any()) } returns appointments
        }

        suspend fun slots(from: LocalDate, to: LocalDate = from, count: Int = 2): Result<List<DaySlots>> {
            return sut.invoke(businessId, employeeId, listOf(RequestedService(service.id, count)), from, to)
        }
    }

    private fun schedule(hours: Map<DayOfWeek, List<WorkHour>>, dayOffs: List<DayOffRange> = emptyList()): Schedule {
        return Schedule(workingDays = hours.keys.toList(), workingHours = hours, dayOffs = dayOffs)
    }

    private fun hours(from: Int, to: Int): List<WorkHour> {
        return listOf(WorkHour(LocalTime(from, 0), LocalTime(to, 0)))
    }

    private fun settings(
        schedule: Schedule,
        timeZone: TimeZone = kyiv,
        breakInMinutes: Int = 0,
    ): AppointmentSettings {
        return AppointmentSettings.stub().copy(
            timeZone = timeZone,
            schedule = schedule,
            inBetweenBreakInMinutes = breakInMinutes,
        )
    }

    private fun LocalDate.at(hour: Int, minute: Int = 0, timeZone: TimeZone = kyiv): Instant {
        return atTime(hour, minute).toInstant(timeZone)
    }

    @Test
    fun `should give every quarter-hour start whose whole duration fits the working hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))

        whenn()
        val result = fixture.slots(monday)

        then()
        val expected = listOf(0, 15, 30, 45, 60, 75, 90, 105, 120).map { monday.at(9) + it.minutes }
        assertEquals(listOf(monday), result.getOrThrow().map { it.date })
        assertEquals(expected, result.getOrThrow().single().slots.map { it.start })
        assertEquals(TimeSlot(monday.at(9), monday.at(10)), result.getOrThrow().single().slots.first())
    }

    @Test
    fun `should give only starts inside the employee's own hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val business = schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))
        fixture.givenBusiness(settings(business), employeeSchedule = schedule(mapOf(DayOfWeek.MONDAY to hours(10, 12))))

        whenn()
        val result = fixture.slots(monday)

        then()
        val expected = listOf(monday.at(10), monday.at(10, 15), monday.at(10, 30), monday.at(10, 45), monday.at(11))
        assertEquals(expected, result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should keep the break around the employee's appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val appointment = BookedRange.stub(start = monday.at(10), end = monday.at(10, 30))
        fixture.givenBusiness(
            settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 15))), breakInMinutes = 10),
            appointments = listOf(appointment),
        )

        whenn()
        val result = fixture.slots(monday)

        then()
        val expected = (0 until 14).map { monday.at(10, 45) + (it * 15).minutes }
        assertEquals(expected, result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should give an empty list for a day off and keep every date of the range in order`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val tuesday = monday.plus(DatePeriod(days = 1))
        val week = DayOfWeek.entries.associateWith { hours(9, 10) }
        fixture.givenBusiness(settings(schedule(week, dayOffs = listOf(DayOffRange(monday, monday)))))

        whenn()
        val result = fixture.slots(monday, tuesday)

        then()
        assertEquals(listOf(monday, tuesday), result.getOrThrow().map { it.date })
        assertTrue(result.getOrThrow()[0].slots.isEmpty())
        assertEquals(listOf(tuesday.at(9)), result.getOrThrow()[1].slots.map { it.start })
    }

    @Test
    fun `should give the starts of an overnight range to the date each one falls on`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val overnight = listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.FRIDAY to overnight))))

        whenn()
        val result = fixture.slots(friday, saturday, count = 1)

        then()
        val fridayStarts = (0 until 8).map { friday.at(22) + (it * 15).minutes }
        val saturdayStarts = (0 until 7).map { saturday.at(0) + (it * 15).minutes }
        assertEquals(fridayStarts, result.getOrThrow()[0].slots.map { it.start })
        assertEquals(saturdayStarts, result.getOrThrow()[1].slots.map { it.start })
    }

    @Test
    fun `should give no starts in the past`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val pastMonday = LocalDate(2020, 6, 1)
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))

        whenn()
        val result = fixture.slots(pastMonday)

        then()
        assertEquals(listOf(DaySlots(pastMonday, emptyList())), result.getOrThrow())
    }

    @Test
    fun `should align the grid to the business time zone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val india = TimeZone.of("Asia/Kolkata")
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 10))), timeZone = india))

        whenn()
        val result = fixture.slots(monday)

        then()
        assertEquals(listOf(monday.at(9, timeZone = india)), result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should read the employee's bookings for the whole range plus the break and one booking's length`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val tuesday = monday.plus(DatePeriod(days = 1))
            fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12))), breakInMinutes = 10))

            whenn()
            fixture.slots(monday, tuesday)

            then()
            val searched = monday.at(0) - 10.minutes..<tuesday.plus(DatePeriod(days = 1)).at(0) + 70.minutes
            coVerify(exactly = 1) {
                fixture.appointmentDataSource.getEmployeeBookings(fixture.businessId, fixture.employeeId, searched)
            }
        }

    @Test
    fun `should accept a range of exactly 31 days`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))

        whenn()
        val result = fixture.slots(monday, monday.plus(DatePeriod(days = 30)))

        then()
        assertEquals(31, result.getOrThrow().size)
    }

    @Test
    fun `should return not found when the business does not take appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.subscriptionDataSource.isBusinessEnabled(fixture.businessId) } returns true
        coEvery { fixture.settingsDataSource.get(fixture.businessId) } returns null

        whenn()
        val result = fixture.slots(monday)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any(), any()) }
    }

    @Test
    fun `should pass on the business service's error for a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.subscriptionDataSource.isBusinessEnabled(fixture.businessId) } returns true
        coEvery { fixture.settingsDataSource.get(fixture.businessId) } returns AppointmentSettings.stub()
        coEvery { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any(), any()) } returns
            Result.failure(GetAppointmentRescheduleContext.Error.EmployeeSuspended())

        whenn()
        val result = fixture.slots(monday)

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentRescheduleContext.Error.EmployeeSuspended)
    }

    @Test
    fun `should reject an empty service list without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(fixture.businessId, fixture.employeeId, emptyList(), monday, monday)

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidServiceSelection)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject a service count below one`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.slots(monday, count = 0)

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidServiceSelection)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject a repeated service id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val repeated = List(2) { RequestedService(fixture.service.id, 1) }

        whenn()
        val result = fixture.sut.invoke(fixture.businessId, fixture.employeeId, repeated, monday, monday)

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidServiceSelection)
    }

    @Test
    fun `should reject more than 100 services in total`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.slots(monday, count = 101)

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidServiceSelection)
    }

    @Test
    fun `should reject a range that ends before it starts without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.slots(friday, monday)

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidRange)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should reject a range longer than 31 days`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.slots(monday, monday.plus(DatePeriod(days = 31)))

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidRange)
    }

    @Test
    fun `should return not found when the business turned appointments off`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.subscriptionDataSource.isBusinessEnabled(fixture.businessId) } returns false
        coEvery { fixture.settingsDataSource.get(fixture.businessId) } returns AppointmentSettings.stub()

        whenn()
        val result = fixture.slots(monday)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any(), any()) }
    }

    @Test
    fun `should reject a date beyond the supported calendar without opening a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val farFuture = LocalDate(10000, 1, 1)

        whenn()
        val result = fixture.slots(farFuture)

        then()
        assertTrue(result.exceptionOrNull() is GetAvailableSlots.Error.InvalidRange)
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should give only the starts still ahead today`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.clock.now = monday.at(10, 5)
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))

        whenn()
        val result = fixture.slots(monday, count = 1)

        then()
        val expected = (0 until 6).map { monday.at(10, 15) + (it * 15).minutes }
        assertEquals(expected, result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should give a start that begins exactly now`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.clock.now = monday.at(11, 30)
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))

        whenn()
        val result = fixture.slots(monday, count = 1)

        then()
        assertEquals(listOf(monday.at(11, 30)), result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should give every quarter hour of the 25-hour day the clocks go back, the repeated hour twice`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val sunday = LocalDate(2099, 10, 25)
            fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.SUNDAY to hours(0, 6)))))

            whenn()
            val result = fixture.slots(sunday)

            then()
            val starts = result.getOrThrow().single().slots.map { it.start }
            assertEquals((0 until 25).map { sunday.at(0) + (it * 15).minutes }, starts)
            assertEquals(2, starts.count { it.toLocalDateTime(kyiv).time == LocalTime(3, 0) })
        }

    @Test
    fun `should give no starts in the hour skipped when the clocks go forward`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val sunday = LocalDate(2099, 3, 29)
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.SUNDAY to hours(0, 6)))))

        whenn()
        val result = fixture.slots(sunday)

        then()
        assertEquals(
            (0 until 17).map {
                sunday.at(0) + (it * 15).minutes
            },
            result.getOrThrow().single().slots.map { it.start },
        )
    }

    @Test
    fun `should let an appointment from the night before block the first starts of the range`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val tuesday = LocalDate(2099, 6, 2)
        val lateNight = BookedRange.stub(start = monday.at(23), end = tuesday.at(0, 5))
        fixture.givenBusiness(
            settings(schedule(mapOf(DayOfWeek.TUESDAY to hours(0, 3))), breakInMinutes = 10),
            appointments = listOf(lateNight),
        )

        whenn()
        val result = fixture.slots(tuesday, count = 1)

        then()
        val expected = (0 until 10).map { tuesday.at(0, 15) + (it * 15).minutes }
        assertEquals(expected, result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should let an appointment after the last date block a late start running past midnight`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val tuesday = LocalDate(2099, 6, 2)
        val overnight = listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))
        val afterMidnight = BookedRange.stub(start = tuesday.at(0, 20), end = tuesday.at(1))
        fixture.givenBusiness(
            settings(schedule(mapOf(DayOfWeek.MONDAY to overnight)), breakInMinutes = 10),
            appointments = listOf(afterMidnight),
        )

        whenn()
        val result = fixture.slots(monday, count = 1)

        then()
        val expected = (0 until 7).map { monday.at(22) + (it * 15).minutes }
        assertEquals(expected, result.getOrThrow().single().slots.map { it.start })
    }

    @Test
    fun `should add up the durations of several services with their counts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val longer = Service.stub(businessId = fixture.businessId, duration = 45.minutes)
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))
        coEvery {
            fixture.businessClient.getAppointmentRescheduleContext(
                fixture.businessId,
                fixture.employeeId,
                listOf(fixture.service.id, longer.id),
                false,
            )
        } returns Result.success(
            AppointmentRescheduleContext(
                employee = Employee.stub(id = fixture.employeeId, schedule = Schedule()),
                services = listOf(fixture.service, longer),
            ),
        )
        val services = listOf(RequestedService(fixture.service.id, 2), RequestedService(longer.id, 1))

        whenn()
        val result = fixture.sut.invoke(fixture.businessId, fixture.employeeId, services, monday, monday)

        then()
        val slots = result.getOrThrow().single().slots
        assertEquals((0 until 6).map { monday.at(9) + (it * 15).minutes }, slots.map { it.start })
        assertEquals(monday.at(10, 45), slots.first().end)
    }

    @Test
    fun `should report a missing service when the business answers without one of them`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))
        coEvery { fixture.businessClient.getAppointmentRescheduleContext(any(), any(), any(), any()) } returns
            Result.success(AppointmentRescheduleContext(employee = Employee.stub(), services = emptyList()))

        whenn()
        val result = fixture.slots(monday)

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentRescheduleContext.Error.ServiceNotFound)
    }

    @Test
    fun `should give no starts when the services take longer than any working range`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBusiness(settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))))

        whenn()
        val result = fixture.slots(monday, count = 7)

        then()
        assertEquals(listOf(DaySlots(monday, emptyList())), result.getOrThrow())
    }

    @Test
    fun `should give no starts for an employee without working hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBusiness(
            settings(schedule(mapOf(DayOfWeek.MONDAY to hours(9, 12)))),
            employeeSchedule = Schedule.empty(),
        )

        whenn()
        val result = fixture.slots(monday)

        then()
        assertEquals(listOf(DaySlots(monday, emptyList())), result.getOrThrow())
    }

    @Test
    fun `should give no starts on a business day off even when the employee works`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val hours = mapOf(DayOfWeek.MONDAY to hours(9, 12))
        fixture.givenBusiness(
            settings(schedule(hours, dayOffs = listOf(DayOffRange(monday, monday)))),
            employeeSchedule = schedule(hours),
        )

        whenn()
        val result = fixture.slots(monday)

        then()
        assertEquals(listOf(DaySlots(monday, emptyList())), result.getOrThrow())
    }
}
