package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class HistoryDatesTest {

    private val warsaw = TimeZone.of("Europe/Warsaw")

    @Test
    fun `should cover the given days from midnight to midnight in the time zone`() = runUnitTest {
        given()
        val dates = HistoryDates(from = LocalDate(2027, 3, 1), to = LocalDate(2027, 3, 2))

        whenn()
        val bounds = dates.startBounds(warsaw)

        then()
        assertEquals(Instant.parse("2027-02-28T23:00:00Z"), bounds.from)
        assertEquals(Instant.parse("2027-03-02T23:00:00Z"), bounds.until)
    }

    @Test
    fun `should leave a side open when its date is not given`() = runUnitTest {
        given()
        val dates = HistoryDates(from = null, to = LocalDate(2027, 3, 1))

        whenn()
        val bounds = dates.startBounds(TimeZone.UTC)

        then()
        assertNull(bounds.from)
        assertEquals(Instant.parse("2027-03-02T00:00:00Z"), bounds.until)
    }

    @Test
    fun `should accept a range of one day`() = runUnitTest {
        given()
        val day = LocalDate(2027, 3, 1)

        whenn()
        val dates = HistoryDates(from = day, to = day)

        then()
        assertTrue(dates.isValid)
    }

    @Test
    fun `should reject a range that ends before it starts`() = runUnitTest {
        given()
        val from = LocalDate(2027, 3, 2)

        whenn()
        val dates = HistoryDates(from = from, to = LocalDate(2027, 3, 1))

        then()
        assertFalse(dates.isValid)
    }

    @Test
    fun `should need no time zone when no date is given`() = runUnitTest {
        given()
        val dates = HistoryDates()

        whenn()
        val isOpen = dates.isOpen

        then()
        assertTrue(isOpen)
    }
}
