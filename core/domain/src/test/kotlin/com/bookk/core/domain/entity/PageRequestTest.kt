package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PageRequestTest {

    @Test
    fun `should read the first page with the default limit when nothing is given`() = runUnitTest {
        given()
        val limit: Int? = null

        whenn()
        val request = PageRequest.of(limit = limit, cursor = null).getOrThrow()

        then()
        assertEquals(PageRequest.DEFAULT_LIMIT, request.limit)
        assertNull(request.after)
    }

    @Test
    fun `should accept the smallest and the largest limit`() = runUnitTest {
        given()
        val limits = listOf(1, PageRequest.MAX_LIMIT)

        whenn()
        val requests = limits.map { PageRequest.of(limit = it, cursor = null).getOrThrow() }

        then()
        assertEquals(limits, requests.map { it.limit })
    }

    @Test
    fun `should fetch one row more than the limit`() = runUnitTest {
        given()
        val request = PageRequest.of(limit = 20, cursor = null).getOrThrow()

        whenn()
        val fetchSize = request.fetchSize

        then()
        assertEquals(21, fetchSize)
    }

    @Test
    fun `should reject a limit below one`() = runUnitTest {
        given()
        val limit = 0

        whenn()
        val result = PageRequest.of(limit = limit, cursor = null)

        then()
        assertTrue(result.exceptionOrNull() is PageError.LimitInvalid)
    }

    @Test
    fun `should reject a limit above the maximum`() = runUnitTest {
        given()
        val limit = PageRequest.MAX_LIMIT + 1

        whenn()
        val result = PageRequest.of(limit = limit, cursor = null)

        then()
        assertTrue(result.exceptionOrNull() is PageError.LimitInvalid)
    }

    @Test
    fun `should continue after the position the cursor holds`() = runUnitTest {
        given()
        val cursor = PageCursor(after = Instant.fromEpochSeconds(1_800_000_000), id = Uuid.random())

        whenn()
        val request = PageRequest.of(limit = null, cursor = cursor.encode()).getOrThrow()

        then()
        assertEquals(cursor, request.after)
    }

    @Test
    fun `should reject a cursor that does not decode`() = runUnitTest {
        given()
        val cursor = "garbage"

        whenn()
        val result = PageRequest.of(limit = null, cursor = cursor)

        then()
        assertTrue(result.exceptionOrNull() is PageError.CursorInvalid)
    }

    @Test
    fun `should reject a cursor longer than any cursor the server encodes`() = runUnitTest {
        given()
        val cursor = "A".repeat(PageRequest.MAX_CURSOR_LENGTH + 1)

        whenn()
        val result = PageRequest.of(limit = null, cursor = cursor)

        then()
        assertTrue(result.exceptionOrNull() is PageError.CursorInvalid)
    }

    @Test
    fun `should report the limit error before the cursor error`() = runUnitTest {
        given()
        val limit = 0

        whenn()
        val result = PageRequest.of(limit = limit, cursor = "garbage")

        then()
        assertTrue(result.exceptionOrNull() is PageError.LimitInvalid)
    }

    @Test
    fun `should read the first page when the cursor is empty`() = runUnitTest {
        given()
        val cursor = ""

        whenn()
        val request = PageRequest.of(limit = null, cursor = cursor).getOrThrow()

        then()
        assertNull(request.after)
    }
}
