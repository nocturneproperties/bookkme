package com.bookk.notifications.domain.impl.notification.renderer

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class DateTimeFormattingTest {

    @Test
    fun `should format instant in English using UTC`() = runUnitTest {
        given()
        val instant = Instant.parse("2026-03-05T14:30:00Z")

        whenn()
        val formatted = instant.formatLocalized(TimeZone.UTC, Language.EN)

        then()
        assertEquals("Mar 5, 2026 2:30 PM", formatted)
    }

    @Test
    fun `should format instant in Ukrainian using UTC`() = runUnitTest {
        given()
        val instant = Instant.parse("2026-03-05T14:30:00Z")

        whenn()
        val formatted = instant.formatLocalized(TimeZone.UTC, Language.UK)

        then()
        assertEquals("5 березня 2026 р., 14:30", formatted)
    }

    @Test
    fun `should convert to the given time zone before formatting`() = runUnitTest {
        given()
        val instant = Instant.parse("2026-03-05T22:30:00Z")

        whenn()
        val formatted = instant.formatLocalized(TimeZone.of("Pacific/Auckland"), Language.EN)

        then()
        assertEquals("Mar 6, 2026 11:30 AM", formatted)
    }
}
