package com.bookk.core.data.eventstreaming.data.datasource

import com.bookk.core.data.eventstreaming.data.orm.table.OutboxEventTable
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OutboxEventDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(OutboxEventTable)
        val sut = OutboxEventDataSourceImpl()
    }

    @Test
    fun `should return an enqueued event with its topic, partition key and payload`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val payload = byteArrayOf(1, 2, 3)

        whenn()
        suspendTransaction { fixture.sut.enqueue(topic = "topic.one", partitionKey = "key", payload = payload) }
        val pending = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        then()
        val event = pending.single()
        assertEquals("topic.one", event.topic)
        assertEquals("key", event.partitionKey)
        assertArrayEquals(payload, event.payload)
    }

    @Test
    fun `should keep a missing partition key as null`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        suspendTransaction { fixture.sut.enqueue(topic = "topic.one", partitionKey = null, payload = byteArrayOf(1)) }
        val pending = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        then()
        assertNull(pending.single().partitionKey)
    }

    @Test
    fun `should return pending events in the order they were enqueued`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topics = listOf("topic.first", "topic.second", "topic.third")

        whenn()
        topics.forEach { topic ->
            suspendTransaction { fixture.sut.enqueue(topic = topic, partitionKey = null, payload = byteArrayOf(1)) }
        }
        val pending = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        then()
        assertEquals(topics, pending.map { it.topic })
    }

    @Test
    fun `should return at most the requested number of pending events, oldest first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        listOf("topic.first", "topic.second", "topic.third").forEach { topic ->
            suspendTransaction { fixture.sut.enqueue(topic = topic, partitionKey = null, payload = byteArrayOf(1)) }
        }

        whenn()
        val pending = suspendTransaction { fixture.sut.lockPending(limit = 2) }

        then()
        assertEquals(listOf("topic.first", "topic.second"), pending.map { it.topic })
    }

    @Test
    fun `should return no pending events when the outbox is empty`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val pending = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        then()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `should delete only the given events`() = runUnitTest {
        given()
        val fixture = SutFixture()
        listOf("topic.first", "topic.second", "topic.third").forEach { topic ->
            suspendTransaction { fixture.sut.enqueue(topic = topic, partitionKey = null, payload = byteArrayOf(1)) }
        }
        val enqueued = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        whenn()
        suspendTransaction { fixture.sut.delete(enqueued.take(2).map { it.id }) }
        val remaining = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        then()
        assertEquals(listOf("topic.third"), remaining.map { it.topic })
    }

    @Test
    fun `should not enqueue an event when the surrounding transaction rolls back`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        runCatching {
            suspendTransaction {
                fixture.sut.enqueue(topic = "topic.one", partitionKey = null, payload = byteArrayOf(1))
                throw IllegalStateException("business write failed")
            }
        }
        val pending = suspendTransaction { fixture.sut.lockPending(limit = 10) }

        then()
        assertTrue(pending.isEmpty())
    }
}
