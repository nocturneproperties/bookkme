package com.bookk.appointments.domain.impl.operation

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class DemoBookingPlanTest {

    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val warsaw = TimeZone.of("Europe/Warsaw")

    @Test
    fun `should spread appointments over the three days before and after today in the business time zone`() =
        runUnitTest {
            given()
            val timeZone = warsaw

            whenn()
            val starts = DemoBookingPlan.appointmentStarts(now, timeZone).map { it.toLocalDateTime(timeZone) }

            then()
            assertEquals((22..28).map { LocalDate(2026, 11, it) }, starts.map { it.date }.distinct())
            assertEquals(
                setOf(LocalTime(10, 0), LocalTime(13, 0), LocalTime(15, 30)),
                starts.map { it.time }.toSet(),
            )
        }

    @Test
    fun `should only propose requests after today`() = runUnitTest {
        given()
        val timeZone = warsaw

        whenn()
        val starts = DemoBookingPlan.requestStarts(now, timeZone)

        then()
        assertTrue(starts.all { it.toLocalDateTime(timeZone).date > LocalDate(2026, 11, 25) }) { starts.toString() }
        assertTrue(starts.all { it.toLocalDateTime(timeZone).time == LocalTime(11, 30) }) { starts.toString() }
    }
}
