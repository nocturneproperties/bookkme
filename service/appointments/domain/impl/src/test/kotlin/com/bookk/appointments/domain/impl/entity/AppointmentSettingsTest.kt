package com.bookk.appointments.domain.impl.entity

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AppointmentSettingsTest {

    private fun settingsWithDayOffs(dayOffs: List<DayOffRange>) = AppointmentSettings.stub(businessId = Uuid.random()).copy(
        schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            workingHours = mapOf(),
            dayOffs = dayOffs
        )
    )

    private fun settingsWithWorkingHours(workingHours: List<WorkHour>) = AppointmentSettings.stub(businessId = Uuid.random()).copy(
        schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY),
            workingHours = mapOf(DayOfWeek.MONDAY to workingHours)
        )
    )

    @Test
    fun `should return true when date is a working day with no day-offs`() = runUnitTest {
        given()
        val settings = settingsWithDayOffs(dayOffs = listOf())
        val monday = LocalDate(2026, 6, 22).atStartOfDayIn(settings.timeZone)

        whenn()
        val result = settings.isInWorkday(monday)

        then()
        assertTrue(result)
    }

    @Test
    fun `should return false when date falls inside a day-off range`() = runUnitTest {
        given()
        val range = DayOffRange(start = LocalDate(2026, 6, 22), end = LocalDate(2026, 6, 26))
        val settings = settingsWithDayOffs(dayOffs = listOf(range))
        val wednesday = LocalDate(2026, 6, 24).atStartOfDayIn(settings.timeZone)

        whenn()
        val result = settings.isInWorkday(wednesday)

        then()
        assertFalse(result)
    }

    @Test
    fun `should return false when date is the boundary of a day-off range`() = runUnitTest {
        given()
        val range = DayOffRange(start = LocalDate(2026, 6, 22), end = LocalDate(2026, 6, 26))
        val settings = settingsWithDayOffs(dayOffs = listOf(range))
        val rangeEnd = LocalDate(2026, 6, 26).atStartOfDayIn(settings.timeZone)

        whenn()
        val result = settings.isInWorkday(rangeEnd)

        then()
        assertFalse(result)
    }

    @Test
    fun `should return true when date is outside any day-off range`() = runUnitTest {
        given()
        val range = DayOffRange(start = LocalDate(2026, 6, 22), end = LocalDate(2026, 6, 24))
        val settings = settingsWithDayOffs(dayOffs = listOf(range))
        val friday = LocalDate(2026, 6, 26).atStartOfDayIn(settings.timeZone)

        whenn()
        val result = settings.isInWorkday(friday)

        then()
        assertTrue(result)
    }

    @Test
    fun `should return false when date is not a working day regardless of day-offs`() = runUnitTest {
        given()
        val settings = settingsWithDayOffs(dayOffs = listOf())
        val saturday = LocalDate(2026, 6, 27).atStartOfDayIn(settings.timeZone)

        whenn()
        val result = settings.isInWorkday(saturday)

        then()
        assertFalse(result)
    }

    @Test
    fun `should return true when appointment range is fully contained in a working time slot`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))))
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(10, 0).toInstant(settings.timeZone)
        val end = monday.atTime(11, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertTrue(result)
    }

    @Test
    fun `should return true when appointment range exactly matches a working time slot`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))))
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(9, 0).toInstant(settings.timeZone)
        val end = monday.atTime(17, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertTrue(result)
    }

    @Test
    fun `should return false when appointment starts before the working time slot`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))))
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(8, 0).toInstant(settings.timeZone)
        val end = monday.atTime(10, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertFalse(result)
    }

    @Test
    fun `should return false when appointment ends after the working time slot`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))))
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(16, 0).toInstant(settings.timeZone)
        val end = monday.atTime(18, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertFalse(result)
    }

    @Test
    fun `should return false when no working time slot is defined for the day`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(listOf())
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(10, 0).toInstant(settings.timeZone)
        val end = monday.atTime(11, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertFalse(result)
    }

    @Test
    fun `should return true when appointment fits within one of multiple working time slots`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(
            listOf(
                WorkHour(LocalTime(9, 0), LocalTime(12, 0)),
                WorkHour(LocalTime(13, 0), LocalTime(17, 0))
            )
        )
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(14, 0).toInstant(settings.timeZone)
        val end = monday.atTime(15, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertTrue(result)
    }

    @Test
    fun `should return false when appointment spans across the gap between two working time slots`() = runUnitTest {
        given()
        val settings = settingsWithWorkingHours(
            listOf(
                WorkHour(LocalTime(9, 0), LocalTime(12, 0)),
                WorkHour(LocalTime(13, 0), LocalTime(17, 0))
            )
        )
        val monday = LocalDate(2026, 6, 22)
        val start = monday.atTime(11, 0).toInstant(settings.timeZone)
        val end = monday.atTime(14, 0).toInstant(settings.timeZone)

        whenn()
        val result = settings.isInWorktime(start, end)

        then()
        assertFalse(result)
    }
}
