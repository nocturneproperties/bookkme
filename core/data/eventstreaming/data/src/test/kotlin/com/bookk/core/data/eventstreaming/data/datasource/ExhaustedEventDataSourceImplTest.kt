package com.bookk.core.data.eventstreaming.data.datasource

import com.bookk.core.data.eventstreaming.DltEvent
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
}
