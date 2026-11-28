package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class BookedRangeTest {

    private val start = Instant.parse("2026-06-22T10:00:00Z")
    private val booked = BookedRange.stub(start = start, end = start + 60.minutes)

    @Test
    fun `should block a range that overlaps the booking`() = runUnitTest {
        given()
        val rangeStart = start + 30.minutes

        whenn()
        val blocks = booked.blocks(rangeStart, rangeStart + 60.minutes, Duration.ZERO)

        then()
        assertTrue(blocks)
    }

    @Test
    fun `should not block a range that touches the booking when there is no gap`() = runUnitTest {
        given()
        val rangeStart = start + 60.minutes

        whenn()
        val after = booked.blocks(rangeStart, rangeStart + 30.minutes, Duration.ZERO)
        val before = booked.blocks(start - 30.minutes, start, Duration.ZERO)

        then()
        assertFalse(after)
        assertFalse(before)
    }

    @Test
    fun `should block a range that starts inside the gap after the booking`() = runUnitTest {
        given()
        val rangeStart = start + 65.minutes

        whenn()
        val blocks = booked.blocks(rangeStart, rangeStart + 30.minutes, 10.minutes)

        then()
        assertTrue(blocks)
    }

    @Test
    fun `should block a range that ends inside the gap before the booking`() = runUnitTest {
        given()
        val rangeEnd = start - 5.minutes

        whenn()
        val blocks = booked.blocks(rangeEnd - 30.minutes, rangeEnd, 10.minutes)

        then()
        assertTrue(blocks)
    }

    @Test
    fun `should not block a range that keeps the full gap on both sides`() = runUnitTest {
        given()
        val afterStart = start + 70.minutes
        val beforeEnd = start - 10.minutes

        whenn()
        val after = booked.blocks(afterStart, afterStart + 30.minutes, 10.minutes)
        val before = booked.blocks(beforeEnd - 30.minutes, beforeEnd, 10.minutes)

        then()
        assertFalse(after)
        assertFalse(before)
    }
}
