package library.schedule

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class ScheduleCoverageTest {

    private val timeZone = TimeZone.of("Europe/Kyiv")
    private val monday = LocalDate(2026, 6, 22)
    private val tuesday = LocalDate(2026, 6, 23)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant {
        return date.atTime(hour, minute).toInstant(timeZone)
    }

    private fun schedule(
        hours: Map<DayOfWeek, List<WorkHour>>,
        workingDays: List<DayOfWeek> = hours.keys.toList(),
        dayOffs: List<DayOffRange> = emptyList(),
    ): Schedule {
        return Schedule(workingDays = workingDays, workingHours = hours, dayOffs = dayOffs)
    }

    @Test
    fun `should cover a range inside one working time slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.covers(at(monday, 10), at(monday, 11), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should cover a range that exactly matches a working time slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.covers(at(monday, 9), at(monday, 17), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not cover a range that starts before the working time slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.covers(at(monday, 8), at(monday, 10), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not cover a range that ends after the working time slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.covers(at(monday, 16), at(monday, 18), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not cover a range across the gap between two working time slots`() = runUnitTest {
        given()
        val hours = listOf(WorkHour(LocalTime(9, 0), LocalTime(12, 0)), WorkHour(LocalTime(13, 0), LocalTime(17, 0)))
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to hours))

        whenn()
        val result = schedule.covers(at(monday, 11), at(monday, 14), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should cover a range across two working time slots that touch`() = runUnitTest {
        given()
        val hours = listOf(WorkHour(LocalTime(13, 0), LocalTime(17, 0)), WorkHour(LocalTime(9, 0), LocalTime(13, 0)))
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to hours))

        whenn()
        val result = schedule.covers(at(monday, 12), at(monday, 14), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should cover a range that runs from a slot ending at midnight into the next day's slot`() = runUnitTest {
        given()
        val schedule = schedule(
            mapOf(
                DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0))),
                DayOfWeek.TUESDAY to listOf(WorkHour(LocalTime(0, 0), LocalTime(17, 0))),
            ),
        )

        whenn()
        val result = schedule.covers(at(monday, 23), at(tuesday, 1), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not cover a range past midnight when the next day is not a working day`() = runUnitTest {
        given()
        val schedule = schedule(
            hours = mapOf(
                DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0))),
                DayOfWeek.TUESDAY to listOf(WorkHour(LocalTime(0, 0), LocalTime(17, 0))),
            ),
            workingDays = listOf(DayOfWeek.MONDAY),
        )

        whenn()
        val result = schedule.covers(at(monday, 23), at(tuesday, 1), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not cover a range past midnight when the next day is a day off`() = runUnitTest {
        given()
        val schedule = schedule(
            hours = mapOf(
                DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0))),
                DayOfWeek.TUESDAY to listOf(WorkHour(LocalTime(0, 0), LocalTime(17, 0))),
            ),
            dayOffs = listOf(DayOffRange(tuesday, tuesday)),
        )

        whenn()
        val result = schedule.covers(at(monday, 23), at(tuesday, 1), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not cover a range past midnight when the slots leave a gap before midnight`() = runUnitTest {
        given()
        val schedule = schedule(
            mapOf(
                DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(23, 0))),
                DayOfWeek.TUESDAY to listOf(WorkHour(LocalTime(0, 0), LocalTime(17, 0))),
            ),
        )

        whenn()
        val result = schedule.covers(at(monday, 22), at(tuesday, 1), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should cover a range inside a slot that ends after midnight`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))))

        whenn()
        val result = schedule.covers(at(monday, 23), at(tuesday, 1, 30), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should cover a range after midnight on a day off duty inside the previous day's slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))))

        whenn()
        val result = schedule.covers(at(tuesday, 0, 30), at(tuesday, 1), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not cover a range that ends after a slot running past midnight`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))))

        whenn()
        val result = schedule.covers(at(tuesday, 1), at(tuesday, 3), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not cover any range on a day off`() = runUnitTest {
        given()
        val schedule = schedule(
            hours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)),
            dayOffs = listOf(DayOffRange(monday, monday)),
        )

        whenn()
        val result = schedule.covers(at(monday, 10), at(monday, 11), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should treat an active day outside every day off as a workday`() = runUnitTest {
        given()
        val schedule = schedule(
            hours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)),
            dayOffs = listOf(DayOffRange(tuesday, tuesday)),
        )

        whenn()
        val result = schedule.isWorkday(monday)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not treat an inactive day as a workday`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.isWorkday(tuesday)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not treat a day inside a day off range as a workday`() = runUnitTest {
        given()
        val schedule = schedule(
            hours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)),
            dayOffs = listOf(DayOffRange(LocalDate(2026, 6, 20), monday)),
        )

        whenn()
        val result = schedule.isWorkday(monday)

        then()
        assertFalse(result)
    }

    @Test
    fun `should not cover any range with a working time slot that starts and ends at the same time`() = runUnitTest {
        given()
        val nineToNine = WorkHour(LocalTime(9, 0), LocalTime(9, 0))
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(nineToNine)))

        whenn()
        val result = schedule.covers(at(monday, 10), at(monday, 11), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should report an empty working range on an inactive day`() = runUnitTest {
        given()
        val nineToNine = WorkHour(LocalTime(9, 0), LocalTime(9, 0))
        val schedule = schedule(mapOf(DayOfWeek.SUNDAY to listOf(nineToNine)), workingDays = emptyList())

        whenn()
        val result = schedule.hasEmptyWorkHour()

        then()
        assertTrue(result)
    }

    @Test
    fun `should not report an empty working range when every range has a length`() = runUnitTest {
        given()
        val overnight = WorkHour(LocalTime(22, 0), LocalTime(0, 0))
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE, overnight)))

        whenn()
        val result = schedule.hasEmptyWorkHour()

        then()
        assertFalse(result)
    }

    @Test
    fun `should be working at an instant inside a working time slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.isWorkingAt(at(monday, 9), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not be working at the instant a working time slot ends`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)))

        whenn()
        val result = schedule.isWorkingAt(at(monday, 17), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should be working after midnight inside the previous day's slot`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(22, 0), LocalTime(2, 0)))))

        whenn()
        val result = schedule.isWorkingAt(at(tuesday, 1), timeZone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not be working at the midnight a slot running until midnight ends`() = runUnitTest {
        given()
        val schedule = schedule(mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(20, 0), LocalTime(0, 0)))))

        whenn()
        val result = schedule.isWorkingAt(at(tuesday, 0), timeZone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should keep the working week and drop the day offs when copying the working week`() = runUnitTest {
        given()
        val schedule = schedule(
            hours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour.NINE_TO_FIVE)),
            dayOffs = listOf(DayOffRange(monday, monday)),
        )

        whenn()
        val result = schedule.workingWeek()

        then()
        assertEquals(schedule.days, result.days)
        assertTrue(result.dayOffs.isEmpty())
    }
}
