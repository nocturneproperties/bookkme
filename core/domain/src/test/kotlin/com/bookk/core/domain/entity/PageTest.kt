package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PageTest {

    private class Row(val id: Uuid, val at: Instant) {
        fun cursor(): PageCursor {
            return PageCursor(after = at, id = id)
        }
    }

    private fun rows(count: Int): List<Row> {
        return List(count) { Row(Uuid.random(), Instant.fromEpochSeconds(1_800_000_000L - it)) }
    }

    @Test
    fun `should keep the limit and point the next cursor at the last kept row when a row is left`() = runUnitTest {
        given()
        val request = PageRequest.of(limit = 2, cursor = null).getOrThrow()
        val fetched = rows(request.fetchSize)

        whenn()
        val page = fetched.toPage(request) { it.cursor() }

        then()
        assertEquals(fetched.take(2), page.items)
        assertEquals(fetched[1].cursor(), page.next)
    }

    @Test
    fun `should have no next cursor on the last page`() = runUnitTest {
        given()
        val request = PageRequest.of(limit = 2, cursor = null).getOrThrow()
        val fetched = rows(2)

        whenn()
        val page = fetched.toPage(request) { it.cursor() }

        then()
        assertEquals(fetched, page.items)
        assertNull(page.next)
        assertNull(page.nextCursor)
    }

    @Test
    fun `should keep the next cursor when mapping the items`() = runUnitTest {
        given()
        val request = PageRequest.of(limit = 1, cursor = null).getOrThrow()
        val page = rows(request.fetchSize).toPage(request) { it.cursor() }

        whenn()
        val mapped = page.map { it.id }

        then()
        assertEquals(listOf(page.items.single().id), mapped.items)
        assertEquals(page.next, mapped.next)
        assertEquals(page.next?.encode(), mapped.nextCursor)
    }
}
