package com.bookk.appointments.domain.impl.entity

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookingScheduleError
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class AppointmentSettingsTest {

    private val monday = LocalDate(2026, 6, 22)

    private fun weekdaySchedule(dayOffs: List<DayOffRange> = emptyList()): Schedule {
        val weekdays = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        return Schedule(
            workingDays = weekdays,
            workingHours = weekdays.associateWith { listOf(WorkHour.NINE_TO_FIVE) },
            dayOffs = dayOffs,
        )
    }

    private fun mondaySchedule(workingHours: List<WorkHour>): Schedule {
        return Schedule(
            workingDays = listOf(DayOfWeek.MONDAY),
            workingHours = mapOf(DayOfWeek.MONDAY to workingHours),
        )
    }

    private fun settingsWith(schedule: Schedule): AppointmentSettings {
        return AppointmentSettings.stub().copy(schedule = schedule)
    }

    private fun AppointmentSettings.at(date: LocalDate, hour: Int, minute: Int = 0): Instant {
        return date.atTime(hour, minute).toInstant(timeZone)
    }

    private fun AppointmentSettings.rejectionOf(start: Instant, end: Instant, employeeSchedule: Schedule?): Throwable? {
        return runCatching { requireBookable(start, end, employeeSchedule) }.exceptionOrNull()
    }

    @Test
    fun `should report a slot inside both schedules as bookable`() = runUnitTest {
        given()
        val settings = settingsWith(weekdaySchedule())

        whenn()
        val bookable = settings.isBookable(settings.at(monday, 10), settings.at(monday, 11), weekdaySchedule())

        then()
        assertTrue(bookable)
    }

    @Test
    fun `should report a slot outside the employee hours as not bookable`() = runUnitTest {
        given()
        val settings = settingsWith(weekdaySchedule())
        val employeeSchedule = mondaySchedule(listOf(WorkHour(LocalTime(12, 0), LocalTime(17, 0))))

        whenn()
        val bookable = settings.isBookable(settings.at(monday, 10), settings.at(monday, 11), employeeSchedule)

        then()
        assertFalse(bookable)
    }

    @Test
    fun `should report a slot outside the business hours as not bookable`() = runUnitTest {
        given()
        val settings = settingsWith(weekdaySchedule())

        whenn()
        val bookable = settings.isBookable(settings.at(monday, 16), settings.at(monday, 18), weekdaySchedule())

        then()
        assertFalse(bookable)
    }

    @Test
    fun `should accept a slot inside the business hours on a working day without an employee schedule`() =
        runUnitTest {
            given()
            val settings = settingsWith(weekdaySchedule())

            whenn()
            val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), null)

            then()
            assertNull(error)
        }

    @Test
    fun `should reject with business date error when the date falls inside a business day-off range`() = runUnitTest {
        given()
        val range = DayOffRange(start = LocalDate(2026, 6, 22), end = LocalDate(2026, 6, 26))
        val settings = settingsWith(weekdaySchedule(dayOffs = listOf(range)))
        val wednesday = LocalDate(2026, 6, 24)

        whenn()
        val error = settings.rejectionOf(settings.at(wednesday, 10), settings.at(wednesday, 11), null)

        then()
        assertTrue(error is BookingScheduleError.BusinessDateNotAllowed)
    }

    @Test
    fun `should reject with business date error on the last day of a business day-off range`() = runUnitTest {
        given()
        val range = DayOffRange(start = LocalDate(2026, 6, 22), end = LocalDate(2026, 6, 26))
        val settings = settingsWith(weekdaySchedule(dayOffs = listOf(range)))
        val rangeEnd = LocalDate(2026, 6, 26)

        whenn()
        val error = settings.rejectionOf(settings.at(rangeEnd, 10), settings.at(rangeEnd, 11), null)

        then()
        assertTrue(error is BookingScheduleError.BusinessDateNotAllowed)
    }

    @Test
    fun `should accept a working day outside every business day-off range`() = runUnitTest {
        given()
        val range = DayOffRange(start = LocalDate(2026, 6, 22), end = LocalDate(2026, 6, 24))
        val settings = settingsWith(weekdaySchedule(dayOffs = listOf(range)))
        val friday = LocalDate(2026, 6, 26)

        whenn()
        val error = settings.rejectionOf(settings.at(friday, 10), settings.at(friday, 11), null)

        then()
        assertNull(error)
    }

    @Test
    fun `should reject with business date error when the business does not work that weekday`() = runUnitTest {
        given()
        val settings = settingsWith(weekdaySchedule())
        val saturday = LocalDate(2026, 6, 27)

        whenn()
        val error = settings.rejectionOf(settings.at(saturday, 10), settings.at(saturday, 11), null)

        then()
        assertTrue(error is BookingScheduleError.BusinessDateNotAllowed)
    }

    @Test
    fun `should accept a slot that exactly matches the business working range`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 9), settings.at(monday, 17), null)

        then()
        assertNull(error)
    }

    @Test
    fun `should reject with business time error when the slot starts before the business hours`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 8), settings.at(monday, 10), null)

        then()
        assertTrue(error is BookingScheduleError.BusinessTimeNotAllowed)
    }

    @Test
    fun `should reject with business time error when the slot ends after the business hours`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 16), settings.at(monday, 18), null)

        then()
        assertTrue(error is BookingScheduleError.BusinessTimeNotAllowed)
    }

    @Test
    fun `should reject with business time error when the business has no hours that day`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(emptyList()))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), null)

        then()
        assertTrue(error is BookingScheduleError.BusinessTimeNotAllowed)
    }

    @Test
    fun `should accept a slot inside one of several business working ranges`() = runUnitTest {
        given()
        val ranges = listOf(WorkHour(LocalTime(9, 0), LocalTime(12, 0)), WorkHour(LocalTime(13, 0), LocalTime(17, 0)))
        val settings = settingsWith(mondaySchedule(ranges))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 14), settings.at(monday, 15), null)

        then()
        assertNull(error)
    }

    @Test
    fun `should reject with business time error when the slot spans the gap between business ranges`() =
        runUnitTest {
            given()
            val ranges = listOf(
                WorkHour(LocalTime(9, 0), LocalTime(12, 0)),
                WorkHour(LocalTime(13, 0), LocalTime(17, 0)),
            )
            val settings = settingsWith(mondaySchedule(ranges))

            whenn()
            val error = settings.rejectionOf(settings.at(monday, 11), settings.at(monday, 14), null)

            then()
            assertTrue(error is BookingScheduleError.BusinessTimeNotAllowed)
        }

    @Test
    fun `should reject with employee date error when the business works that day but the employee does not`() =
        runUnitTest {
            given()
            val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))
            val employeeSchedule = Schedule(
                workingDays = listOf(DayOfWeek.TUESDAY),
                workingHours = mapOf(DayOfWeek.TUESDAY to listOf(WorkHour.NINE_TO_FIVE)),
            )

            whenn()
            val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), employeeSchedule)

            then()
            assertTrue(error is BookingScheduleError.EmployeeDateNotAllowed)
        }

    @Test
    fun `should reject with employee date error when the date is a day off of the employee only`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))
        val employeeSchedule = settings.schedule.copy(dayOffs = listOf(DayOffRange(monday, monday)))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), employeeSchedule)

        then()
        assertTrue(error is BookingScheduleError.EmployeeDateNotAllowed)
    }

    @Test
    fun `should reject with business date error when neither the business nor the employee works that day`() =
        runUnitTest {
            given()
            val settings = settingsWith(weekdaySchedule())
            val saturday = LocalDate(2026, 6, 27)

            whenn()
            val error = settings.rejectionOf(settings.at(saturday, 10), settings.at(saturday, 11), Schedule.empty())

            then()
            assertTrue(error is BookingScheduleError.BusinessDateNotAllowed)
        }

    @Test
    fun `should reject with business time error before checking the employee schedule`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 18), settings.at(monday, 19), Schedule.empty())

        then()
        assertTrue(error is BookingScheduleError.BusinessTimeNotAllowed)
    }

    @Test
    fun `should accept a slot when both the business and the employee work at that time`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), settings.schedule)

        then()
        assertNull(error)
    }

    @Test
    fun `should accept a start after midnight inside the previous day's overnight range`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))))
        val tuesday = LocalDate(2026, 6, 23)

        whenn()
        val error = settings.rejectionOf(settings.at(tuesday, 1), settings.at(tuesday, 1, 30), settings.schedule)

        then()
        assertNull(error)
    }

    @Test
    fun `should reject with business date error at the midnight an overnight range ends before a day off`() =
        runUnitTest {
            given()
            val settings = settingsWith(mondaySchedule(listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0)))))
            val tuesday = LocalDate(2026, 6, 23)

            whenn()
            val error = settings.rejectionOf(settings.at(tuesday, 0), settings.at(tuesday, 0, 30), null)

            then()
            assertTrue(error is BookingScheduleError.BusinessDateNotAllowed)
        }

    @Test
    fun `should reject with employee date error at the midnight the employee's overnight range ends`() =
        runUnitTest {
            given()
            val businessSchedule = Schedule(
                workingDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                workingHours = mapOf(
                    DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0))),
                    DayOfWeek.TUESDAY to listOf(WorkHour(LocalTime(0, 0), LocalTime(17, 0))),
                ),
            )
            val settings = settingsWith(businessSchedule)
            val employeeSchedule = mondaySchedule(listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0))))
            val tuesday = LocalDate(2026, 6, 23)

            whenn()
            val error = settings.rejectionOf(settings.at(tuesday, 0), settings.at(tuesday, 0, 30), employeeSchedule)

            then()
            assertTrue(error is BookingScheduleError.EmployeeDateNotAllowed)
        }

    @Test
    fun `should reject with employee time error when the slot is outside the employee working hours`() =
        runUnitTest {
            given()
            val settings = settingsWith(mondaySchedule(listOf(WorkHour.NINE_TO_FIVE)))
            val employeeSchedule = mondaySchedule(listOf(WorkHour(LocalTime(12, 0), LocalTime(17, 0))))

            whenn()
            val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), employeeSchedule)

            then()
            assertTrue(error is BookingScheduleError.EmployeeTimeNotAllowed)
        }

    @Test
    fun `should reject with business time error when the slot is inside the employee hours only`() = runUnitTest {
        given()
        val settings = settingsWith(mondaySchedule(listOf(WorkHour(LocalTime(12, 0), LocalTime(17, 0)))))
        val employeeSchedule = mondaySchedule(listOf(WorkHour.NINE_TO_FIVE))

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 10), settings.at(monday, 11), employeeSchedule)

        then()
        assertTrue(error is BookingScheduleError.BusinessTimeNotAllowed)
    }

    @Test
    fun `should accept a slot that runs past midnight into the next day's hours`() = runUnitTest {
        given()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            workingHours = mapOf(
                DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0))),
                DayOfWeek.TUESDAY to listOf(WorkHour(LocalTime(0, 0), LocalTime(17, 0))),
            ),
        )
        val settings = settingsWith(schedule)
        val tuesday = LocalDate(2026, 6, 23)

        whenn()
        val error = settings.rejectionOf(settings.at(monday, 23), settings.at(tuesday, 1), schedule)

        then()
        assertNull(error)
    }
}
