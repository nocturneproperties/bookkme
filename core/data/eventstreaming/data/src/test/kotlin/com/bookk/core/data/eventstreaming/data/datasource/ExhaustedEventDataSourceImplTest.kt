package com.bookk.core.data.eventstreaming.data.datasource

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.data.orm.table.ExhaustedEventTable
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ExhaustedEventDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(ExhaustedEventTable)
        val sut = ExhaustedEventDataSourceImpl()
    }

    private fun dltEvent(originalTopic: String = "topic.one", attempt: Int = 3) = DltEvent(
        payload = "payload".toByteArray(),
        originalTopic = originalTopic,
        attempt = attempt,
        topic = originalTopic,
        idempotencyKey = Uuid.random().toString()
    )

    @Test
    fun `should save and find an exhausted event by original topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = dltEvent()

        whenn()
        suspendTransaction { fixture.sut.save(event) }
        val found = suspendTransaction { fixture.sut.findByOriginalTopic(event.originalTopic) }

        then()
        assertEquals(1, found.size)
        assertEquals(event, found.single())
    }

    @Test
    fun `should return empty list for a topic with no exhausted events`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.findByOriginalTopic("topic.unknown") }

        then()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `should not find events saved under a different original topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.save(dltEvent(originalTopic = "topic.other")) }

        whenn()
        val found = suspendTransaction { fixture.sut.findByOriginalTopic("topic.one") }

        then()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `should treat saving the same idempotency key twice as already persisted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = dltEvent()
        suspendTransaction { fixture.sut.save(event) }

        whenn()
        suspendTransaction { fixture.sut.save(event) }
        val found = suspendTransaction { fixture.sut.findByOriginalTopic(event.originalTopic) }

        then()
        assertEquals(1, found.size)
    }

    @Test
    fun `should not request a replay of a newly parked event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.save(dltEvent()) }

        whenn()
        val locked = suspendTransaction { fixture.sut.lockReplayRequested(limit = 10) }

        then()
        assertTrue(locked.isEmpty())
    }

    @Test
    fun `should request a replay of every parked event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = dltEvent(originalTopic = "topic.one")
        val second = dltEvent(originalTopic = "topic.two")
        suspendTransaction {
            fixture.sut.save(first)
            fixture.sut.save(second)
        }

        whenn()
        suspendTransaction { fixture.sut.requestReplayOfAll() }
        val locked = suspendTransaction { fixture.sut.lockReplayRequested(limit = 10) }

        then()
        assertEquals(setOf(first, second), locked.map(ExhaustedEvent::event).toSet())
    }

    @Test
    fun `should lock no more requested events than the limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction {
            repeat(3) { fixture.sut.save(dltEvent()) }
            fixture.sut.requestReplayOfAll()
        }

        whenn()
        val locked = suspendTransaction { fixture.sut.lockReplayRequested(limit = 2) }

        then()
        assertEquals(2, locked.size)
    }

    @Test
    fun `should not request a replay of an event parked after the request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requested = dltEvent()
        suspendTransaction {
            fixture.sut.save(requested)
            fixture.sut.requestReplayOfAll()
            fixture.sut.save(dltEvent())
        }

        whenn()
        val locked = suspendTransaction { fixture.sut.lockReplayRequested(limit = 10) }

        then()
        assertEquals(listOf(requested), locked.map(ExhaustedEvent::event))
    }

    @Test
    fun `should delete only the given exhausted events`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deleted = dltEvent()
        val kept = dltEvent()
        suspendTransaction {
            fixture.sut.save(deleted)
            fixture.sut.save(kept)
            fixture.sut.requestReplayOfAll()
        }
        val deletedId = suspendTransaction { fixture.sut.lockReplayRequested(limit = 10) }.single { it.event == deleted }.id

        whenn()
        suspendTransaction { fixture.sut.delete(listOf(deletedId)) }

        then()
        assertEquals(listOf(kept), suspendTransaction { fixture.sut.findByOriginalTopic(kept.originalTopic) })
    }

    @Test
    fun `should keep an event whose replay request is cleared without requesting it again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = dltEvent()
        suspendTransaction {
            fixture.sut.save(event)
            fixture.sut.requestReplayOfAll()
        }
        val id = suspendTransaction { fixture.sut.lockReplayRequested(limit = 10) }.single().id

        whenn()
        suspendTransaction { fixture.sut.clearReplayRequest(listOf(id)) }

        then()
        assertTrue(suspendTransaction { fixture.sut.lockReplayRequested(limit = 10) }.isEmpty())
        assertEquals(listOf(event), suspendTransaction { fixture.sut.findByOriginalTopic(event.originalTopic) })
    }

    @Test
    fun `should ignore an empty list of ids`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = dltEvent()
        suspendTransaction { fixture.sut.save(event) }

        whenn()
        suspendTransaction {
            fixture.sut.delete(emptyList())
            fixture.sut.clearReplayRequest(emptyList())
        }

        then()
        assertEquals(listOf(event), suspendTransaction { fixture.sut.findByOriginalTopic(event.originalTopic) })
    }

    @Test
    fun `should count parked events per original topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction {
            repeat(2) { fixture.sut.save(dltEvent(originalTopic = "topic.one")) }
            fixture.sut.save(dltEvent(originalTopic = "topic.two"))
        }

        whenn()
        val counts = suspendTransaction { fixture.sut.countByOriginalTopic() }

        then()
        assertEquals(mapOf("topic.one" to 2L, "topic.two" to 1L), counts)
    }

    @Test
    fun `should count nothing when no event is parked`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val counts = suspendTransaction { fixture.sut.countByOriginalTopic() }

        then()
        assertTrue(counts.isEmpty())
    }
}
