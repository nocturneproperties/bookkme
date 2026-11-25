package com.bookk.core.data

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.domain.entity.toPage
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class KeysetTest {

    private object KeysetRowTable : UuidTable("keyset_row") {
        val at = timestamp("at")
    }

    private class Row(val id: Uuid, val at: Instant)

    private class SutFixture(order: SortOrder) {
        val db = createTestDatabase(KeysetRowTable)
        val sut = Keyset(KeysetRowTable.at, KeysetRowTable.id, order)

        suspend fun insert(rows: List<Row>) {
            suspendTransaction(db = db) {
                KeysetRowTable.batchInsert(rows) { row ->
                    this[KeysetRowTable.id] = row.id
                    this[KeysetRowTable.at] = row.at
                }
            }
        }

        suspend fun readAllPages(limit: Int): List<List<Uuid>> {
            val pages = mutableListOf<List<Uuid>>()
            var after: PageCursor? = null
            do {
                val request = PageRequest.of(limit = limit, cursor = after?.encode()).getOrThrow()
                val page = suspendTransaction(db = db) {
                    KeysetRowTable.selectAll()
                        .where { sut.after(request.after) }
                        .orderBy(*sut.orderBy())
                        .limit(request.fetchSize)
                        .map { Row(it[KeysetRowTable.id].value, it[KeysetRowTable.at]) }
                        .toPage(request) { PageCursor(after = it.at, id = it.id) }
                }
                pages += page.items.map { it.id }
                after = page.next
            } while (after != null)
            return pages
        }
    }

    private val earlier = Instant.fromEpochSeconds(1_800_000_000)
    private val later = Instant.fromEpochSeconds(1_800_000_060)

    private fun rowsWithSharedTimes(): List<Row> {
        return List(3) { Row(Uuid.random(), earlier) } + List(2) { Row(Uuid.random(), later) }
    }

    @Test
    fun `should walk descending pages without gaps or repeats when rows share a sort key`() = runUnitTest {
        given()
        val fixture = SutFixture(SortOrder.DESC)
        val rows = rowsWithSharedTimes()
        fixture.insert(rows)
        val expected = rows.sortedWith(compareByDescending<Row> { it.at }.thenByDescending { it.id }).map { it.id }

        whenn()
        val pages = fixture.readAllPages(limit = 2)

        then()
        assertEquals(listOf(2, 2, 1), pages.map { it.size })
        assertEquals(expected, pages.flatten())
    }

    @Test
    fun `should walk ascending pages without gaps or repeats when rows share a sort key`() = runUnitTest {
        given()
        val fixture = SutFixture(SortOrder.ASC)
        val rows = rowsWithSharedTimes()
        fixture.insert(rows)
        val expected = rows.sortedWith(compareBy<Row> { it.at }.thenBy { it.id }).map { it.id }

        whenn()
        val pages = fixture.readAllPages(limit = 2)

        then()
        assertEquals(listOf(2, 2, 1), pages.map { it.size })
        assertEquals(expected, pages.flatten())
    }

    @Test
    fun `should read every row on one page when they fit the limit`() = runUnitTest {
        given()
        val fixture = SutFixture(SortOrder.DESC)
        fixture.insert(rowsWithSharedTimes())

        whenn()
        val pages = fixture.readAllPages(limit = 5)

        then()
        assertEquals(listOf(5), pages.map { it.size })
    }
}
