package library.schedule

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ScheduleTest {

    @Test
    fun `should cover all seven days by default`() = runUnitTest {
        given()
        val expectedActiveDays = DayOfWeek.entries.filter { it < DayOfWeek.SATURDAY }

        whenn()
        val schedule = Schedule()

        then()
        assertEquals(DayOfWeek.entries.toSet(), schedule.days.keys)
        assertEquals(expectedActiveDays, schedule.activeDays())
    }

    @Test
    fun `should cover all seven days when built from working days and hours`() = runUnitTest {
        given()
        val hours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0))))

        whenn()
        val schedule = Schedule(workingDays = listOf(DayOfWeek.MONDAY), workingHours = hours)

        then()
        assertEquals(DayOfWeek.entries.toSet(), schedule.days.keys)
        assertEquals(listOf(DayOfWeek.MONDAY), schedule.activeDays())
        assertFalse(schedule[DayOfWeek.SUNDAY].isActive)
        assertTrue(schedule[DayOfWeek.SUNDAY].workingTime.isEmpty())
    }

    @Test
    fun `should reject a schedule that does not cover every day`() = runUnitTest {
        given()
        val incompleteDays = mapOf(
            DayOfWeek.MONDAY to DayOfWeekSchedule(listOf(WorkHour.NINE_TO_FIVE), isActive = true)
        )

        whenn()
        val result = runCatching { Schedule(days = incompleteDays) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `should map working hours back to the day they are keyed under`() = runUnitTest {
        given()
        val monday = listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))
        val schedule = Schedule(workingDays = listOf(DayOfWeek.MONDAY), workingHours = mapOf(DayOfWeek.MONDAY to monday))

        whenn()
        val workingHours = schedule.workingHours()

        then()
        assertEquals(monday, workingHours.getValue(DayOfWeek.MONDAY))
        assertEquals(emptyList<WorkHour>(), workingHours.getValue(DayOfWeek.FRIDAY))
    }

    @Test
    fun `should have no working days and no day offs when empty`() = runUnitTest {
        given()

        whenn()
        val schedule = Schedule.empty()

        then()
        assertEquals(DayOfWeek.entries.toSet(), schedule.days.keys)
        assertTrue(schedule.activeDays().isEmpty())
        assertTrue(schedule.workingHours().values.all { it.isEmpty() })
        assertTrue(schedule.dayOffs.isEmpty())
    }

    @Test
    fun `should round trip active days through the working days mask`() = runUnitTest {
        given()
        val activeDays = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        whenn()
        val restored = activeDays.toWorkingDaysMask().toWorkingDays()

        then()
        assertEquals(activeDays, restored)
    }
}
