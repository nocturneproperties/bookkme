package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PageCursorTest {

    @Test
    fun `should decode the cursor it encoded`() = runUnitTest {
        given()
        val cursor = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000, 123_456_000), id = Uuid.random())

        whenn()
        val decoded = PageCursor.decodeOrNull(cursor.encode())

        then()
        assertEquals(cursor, decoded)
    }

    @Test
    fun `should encode a cursor that is safe in a query string`() = runUnitTest {
        given()
        val cursor = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000), id = Uuid.random())

        whenn()
        val encoded = cursor.encode()

        then()
        assertFalse(encoded.any { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun `should not decode text that is not base64`() = runUnitTest {
        given()
        val text = "not a cursor!"

        whenn()
        val decoded = PageCursor.decodeOrNull(text)

        then()
        assertNull(decoded)
    }

    @Test
    fun `should not decode base64 that does not hold a cursor`() = runUnitTest {
        given()
        val text = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(byteArrayOf(1, 2, 3, 4, 5))

        whenn()
        val decoded = PageCursor.decodeOrNull(text)

        then()
        assertNull(decoded)
    }

    @Test
    fun `should not decode an empty cursor`() = runUnitTest {
        given()
        val text = ""

        whenn()
        val decoded = PageCursor.decodeOrNull(text)

        then()
        assertNull(decoded)
    }
}
