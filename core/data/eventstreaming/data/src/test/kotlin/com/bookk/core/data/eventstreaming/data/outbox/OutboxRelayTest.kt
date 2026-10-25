package com.bookk.core.data.eventstreaming.data.outbox

import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.EventStreaming.RawRecord
import com.bookk.core.data.eventstreaming.OutboxEvent
import com.bookk.core.data.eventstreaming.OutboxEventDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class OutboxRelayTest {

    private class SutFixture {
        val outboxEventDataSource = mockk<OutboxEventDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val rawProducer = mockk<EventStreaming.RawProducer<String>>()
        val batchSize = 2
        val idleDelay = 1.seconds
        val sut = OutboxRelay(outboxEventDataSource, transactionManager, rawProducer, batchSize, idleDelay)
        val published = mutableListOf<RawRecord<String>>()

        init {
            transactionManager.mockTransaction()
            coEvery { rawProducer.publishAll(any()) } coAnswers { published += firstArg<List<RawRecord<String>>>() }
            coEvery { outboxEventDataSource.delete(any()) } just runs
        }

        fun OutboxEvent.isPublished() = assertEquals(
            1,
            published.count { it.topic == topic && it.partitionKey == partitionKey && it.payload.contentEquals(payload) }
        )
    }

    @Test
    fun `should publish every locked event in order and then delete them`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = OutboxEvent.stub(id = 1, topic = "topic.first", partitionKey = "key", payload = byteArrayOf(1))
        val second = OutboxEvent.stub(id = 2, topic = "topic.second", partitionKey = null, payload = byteArrayOf(2))
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(first, second)

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertEquals(2, result.getOrNull())
        assertEquals(listOf("topic.first" to "key", "topic.second" to null), fixture.published.map { it.topic to it.partitionKey })
        assertEquals(listOf(first.payload, second.payload), fixture.published.map { it.payload })
        coVerifyOrder {
            fixture.rawProducer.publishAll(any())
            fixture.outboxEventDataSource.delete(listOf(1L, 2L))
        }
    }

    @Test
    fun `should publish nothing when the outbox is empty`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns emptyList()

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertEquals(0, result.getOrNull())
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun `should fail and keep the batch in the outbox when publishing an event fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = OutboxEvent.stub(id = 1, topic = "topic.first")
        val second = OutboxEvent.stub(id = 2, topic = "topic.second")
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returns listOf(first, second)
        coEvery { fixture.rawProducer.publishAll(any()) } throws IllegalStateException("broker down")

        whenn()
        val result = fixture.sut.relayBatch()

        then()
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { fixture.outboxEventDataSource.delete(any()) }
    }

    @Test
    fun `should relay the next batch immediately while the previous batch was full`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = OutboxEvent.stub(id = 1, topic = "topic.first")
        val second = OutboxEvent.stub(id = 2, topic = "topic.second")
        val third = OutboxEvent.stub(id = 3, topic = "topic.third")
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } returnsMany listOf(
            listOf(first, second),
            listOf(third),
            emptyList()
        )

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()

        then()
        with(fixture) {
            first.isPublished()
            second.isPublished()
            third.isPublished()
        }
        coVerify(exactly = 2) { fixture.outboxEventDataSource.lockPending(fixture.batchSize) }
    }

    @Test
    fun `should wait for the idle delay before polling again after a partial batch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var polls = 0
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } coAnswers {
            polls++
            emptyList()
        }
        fixture.sut.start(backgroundScope)
        runCurrent()

        whenn()
        advanceTimeBy(fixture.idleDelay - 1.milliseconds)
        runCurrent()
        val pollsJustBeforeIdleDelay = polls
        advanceTimeBy(1.milliseconds)
        runCurrent()

        then()
        assertEquals(1, pollsJustBeforeIdleDelay)
        assertEquals(2, polls)
    }

    @Test
    fun `should keep relaying after a batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = OutboxEvent.stub(id = 1, topic = "topic.first")
        coEvery { fixture.outboxEventDataSource.lockPending(fixture.batchSize) } throws
            IllegalStateException("database down") andThen listOf(event) andThen emptyList()

        whenn()
        fixture.sut.start(backgroundScope)
        runCurrent()
        advanceTimeBy(fixture.idleDelay)
        runCurrent()

        then()
        with(fixture) { event.isPublished() }
    }
}
