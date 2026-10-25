package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.FlakyExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KeyedTestEvent
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

internal class EmbeddedDltRetryTest {

    private class SutFixture(
        val exhaustedEventDataSource: ExhaustedEventDataSource = InMemoryExhaustedEventDataSource(),
    ) {
        val topicQueueHolder = TopicQueueHolder<String>()
        val protoBuf = ProtoBuf { encodeDefaults = true }
        val producer = EmbeddedEventProducer(topicQueueHolder, protoBuf)
        val consumer = EmbeddedEventConsumer(
            topicQueueHolder = topicQueueHolder,
            protoBuf = protoBuf,
            exhaustedEventDataSource = exhaustedEventDataSource,
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            consumerGroup = "business_group",
        )
    }

    /**
     * `advanceUntilIdle()` deliberately stops early once only `backgroundScope` work is left pending
     * (see `TestScope.backgroundScope` KDoc) — every consumer here runs via `consumer.start(backgroundScope)`,
     * so it never reliably drains a multi-round dlt retry chain. `advanceTimeBy`/`runCurrent` do not have that
     * special case. One hour of virtual time costs nothing real and comfortably covers any attempt count this
     * suite exercises.
     */
    private suspend fun TestScope.advanceThroughRetries() {
        runCurrent()
        advanceTimeBy(1.hours)
        runCurrent()
    }

    @Test
    fun `should retry a failing event through the dlt topic until it succeeds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.dlt.retry.success"
        val attempts = AtomicInteger(0)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            if (attempts.getAndIncrement() < 2) throw RuntimeException("boom") else received += event
        }
        fixture.consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(1, received.size)
        assertEquals(3, attempts.get())
        assertTrue(fixture.exhaustedEventDataSource.findByOriginalTopic(topic).isEmpty())
    }

    @Test
    fun `should wait for the backoff delay before retrying`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.dlt.retry.backoff-timing"
        val attempts = AtomicInteger(0)
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(topic) {
            attempts.incrementAndGet()
            throw RuntimeException("boom")
        }
        fixture.consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        runCurrent()

        then()
        assertEquals(1, attempts.get())

        advanceTimeBy(DltRetry.backoffMillis(1).milliseconds - 1.milliseconds)
        runCurrent()
        assertEquals(1, attempts.get())

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(2, attempts.get())
    }

    @Test
    fun `should persist an always-failing event to the exhausted store after max attempts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.dlt.retry.exhausted"
        val attempts = AtomicInteger(0)
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(topic) {
            attempts.incrementAndGet()
            throw RuntimeException("boom")
        }
        fixture.consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, attempts.get())
        val exhausted = fixture.exhaustedEventDataSource.findByOriginalTopic(topic)
        assertEquals(1, exhausted.size)
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, exhausted.single().attempt)
    }

    @Test
    fun `should retry persisting without re-invoking the business handler when the save fails`() = runUnitTest {
        given()
        val fixture = SutFixture(exhaustedEventDataSource = FlakyExhaustedEventDataSource(failuresBeforeSuccess = 2))
        val topic = "embedded.dlt.retry.persist"
        val attempts = AtomicInteger(0)
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(topic) {
            attempts.incrementAndGet()
            throw RuntimeException("boom")
        }
        fixture.consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, attempts.get())
        assertEquals(1, fixture.exhaustedEventDataSource.findByOriginalTopic(topic).size)
    }

    @Test
    fun `should replay a persisted exhausted event back onto its original topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.dlt.retry.replay"
        val shouldFail = AtomicInteger(1)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            if (shouldFail.get() == 1) throw RuntimeException("boom") else received += event
        }
        fixture.consumer.start(backgroundScope)
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()
        shouldFail.set(0)

        whenn()
        val dltEvent = fixture.exhaustedEventDataSource.findByOriginalTopic(topic).single()
        fixture.producer.replay(dltEvent, fixture.consumer.typeFor(topic)!!)
        runCurrent()

        then()
        assertEquals(1, received.size)
        assertEquals("entity-1", received.single().entityId)
    }

    @Test
    fun `should isolate retries between two independently registered topics`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val flakyTopic = "embedded.dlt.retry.isolation.flaky"
        val healthyTopic = "embedded.dlt.retry.isolation.healthy"
        val flakyAttempts = AtomicInteger(0)
        val flakyReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val healthyReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(flakyTopic) { event ->
            if (flakyAttempts.getAndIncrement() < 2) throw RuntimeException("boom") else flakyReceived += event
        }
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(healthyTopic) { event -> healthyReceived += event }
        fixture.consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-flaky", sequence = 0, topic = flakyTopic))
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-healthy", sequence = 0, topic = healthyTopic))
        advanceThroughRetries()

        then()
        assertEquals(1, healthyReceived.size)
        assertEquals(1, flakyReceived.size)
        assertEquals(3, flakyAttempts.get())
        assertTrue(fixture.exhaustedEventDataSource.findByOriginalTopic(flakyTopic).isEmpty())
        assertTrue(fixture.exhaustedEventDataSource.findByOriginalTopic(healthyTopic).isEmpty())
    }

    @Test
    fun `should retry an event whose handler threw its own cancellation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.dlt.retry.cancelled"
        val attempts = AtomicInteger(0)
        fixture.consumer.registerReceiver<KeyedTestEvent, String>(topic) {
            attempts.incrementAndGet()
            throw CancellationException("cancelled")
        }
        fixture.consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, attempts.get())
        assertEquals(1, fixture.exhaustedEventDataSource.findByOriginalTopic(topic).size)
    }
}
