package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.EventClaim
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KeyedTestEvent
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class EmbeddedEventIdempotencyTest {

    private class SutFixture(
        val eventIdempotencyStorage: EventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
    ) {
        val topicQueueHolder = TopicQueueHolder<String>()
        val protoBuf = ProtoBuf { encodeDefaults = true }
        val exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        val producer = EmbeddedEventProducer(topicQueueHolder, protoBuf)

        fun newConsumer(consumerGroup: String = "business_group") = EmbeddedEventConsumer(
            topicQueueHolder = topicQueueHolder,
            protoBuf = protoBuf,
            exhaustedEventDataSource = exhaustedEventDataSource,
            eventIdempotencyStorage = eventIdempotencyStorage,
            consumerGroup = consumerGroup,
        )

        fun encode(event: KeyedTestEvent) = protoBuf.encodeToByteArray(KeyedTestEvent.serializer(), event)
    }

    private suspend fun TestScope.advanceThroughRetries() {
        runCurrent()
        advanceTimeBy(1.hours)
        runCurrent()
    }

    @Test
    fun `should handle an event re-published with the same idempotency key only once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.republished"
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> received += event }
        consumer.start(backgroundScope)
        val payload = fixture.encode(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))

        whenn()
        fixture.producer.publish(topic = topic, partitionKey = "entity-1", payload = payload)
        fixture.producer.publish(topic = topic, partitionKey = "entity-1", payload = payload)
        runCurrent()

        then()
        assertEquals(1, received.size)
    }

    @Test
    fun `should handle a directly sent duplicate only once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.direct"
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> received += event }
        consumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)

        whenn()
        fixture.producer.publishEncoded(event)
        fixture.producer.publishEncoded(event)
        runCurrent()

        then()
        assertEquals(1, received.size)
    }

    @Test
    fun `should handle the same event once in every consumer group sharing the storage`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.fan-out"
        val userConsumer = fixture.newConsumer(consumerGroup = "user_group")
        val businessConsumer = fixture.newConsumer(consumerGroup = "business_group")
        val userReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val businessReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        userConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> userReceived += event }
        businessConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> businessReceived += event }
        userConsumer.start(backgroundScope)
        businessConsumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        runCurrent()

        then()
        assertEquals(1, userReceived.size)
        assertEquals(1, businessReceived.size)
    }

    @Test
    fun `should retry a failure only in the consumer group that failed it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.dlt-per-group"
        val userConsumer = fixture.newConsumer(consumerGroup = "user_group")
        val businessConsumer = fixture.newConsumer(consumerGroup = "business_group")
        val userAttempts = AtomicInteger(0)
        val businessAttempts = AtomicInteger(0)
        userConsumer.registerReceiver<KeyedTestEvent, String>(topic) { userAttempts.incrementAndGet(); throw RuntimeException("boom") }
        businessConsumer.registerReceiver<KeyedTestEvent, String>(topic) { businessAttempts.incrementAndGet(); throw RuntimeException("boom") }
        userConsumer.start(backgroundScope)
        businessConsumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, userAttempts.get())
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, businessAttempts.get())
        assertEquals(2, fixture.exhaustedEventDataSource.findByOriginalTopic(topic).size)
    }

    @Test
    fun `should skip a duplicate that arrives after a dlt retry succeeded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.after-retry"
        val consumer = fixture.newConsumer()
        val attempts = AtomicInteger(0)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            if (attempts.getAndIncrement() == 0) throw RuntimeException("boom") else received += event
        }
        consumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)
        fixture.producer.publishEncoded(event)
        advanceThroughRetries()

        whenn()
        fixture.producer.publishEncoded(event)
        advanceThroughRetries()

        then()
        assertEquals(1, received.size)
        assertEquals(2, attempts.get())
    }

    @Test
    fun `should park an event whose claim stays held by an unfinished delivery in the exhausted store without handling it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.in-progress"
        val consumer = fixture.newConsumer(consumerGroup = "business_group")
        val attempts = AtomicInteger(0)
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { attempts.incrementAndGet() }
        consumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)
        fixture.eventIdempotencyStorage.claim("business_group", topic, event.idempotencyKey, Uuid.random().toString())

        whenn()
        fixture.producer.publishEncoded(event)
        advanceThroughRetries()

        then()
        assertEquals(0, attempts.get())
        assertEquals(1, fixture.exhaustedEventDataSource.findByOriginalTopic(topic).size)
    }

    @Test
    fun `should release the claim when the handler is cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "embedded.idempotency.cancelled"
        val consumer = fixture.newConsumer(consumerGroup = "business_group")
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { throw CancellationException("cancelled") }
        consumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)

        whenn()
        fixture.producer.publishEncoded(event)
        runCurrent()

        then()
        assertEquals(EventClaim.CLAIMED, fixture.eventIdempotencyStorage.claim("business_group", topic, event.idempotencyKey, Uuid.random().toString()))
    }

    @Test
    fun `should retry an event through the dlt when the claim cannot be taken`() = runUnitTest {
        given()
        val storage = mockk<EventIdempotencyStorage>()
        every { storage.claimTtl } returns 1.minutes
        coEvery { storage.claim(any(), any(), any(), any()) } throws RuntimeException("cache down") andThen EventClaim.CLAIMED
        coEvery { storage.markProcessed(any(), any(), any()) } just Runs
        val fixture = SutFixture(eventIdempotencyStorage = storage)
        val topic = "embedded.idempotency.claim-failure"
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> received += event }
        consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(1, received.size)
        assertTrue(fixture.exhaustedEventDataSource.findByOriginalTopic(topic).isEmpty())
    }

    @Test
    fun `should not handle the event again when marking it processed fails`() = runUnitTest {
        given()
        val storage = mockk<EventIdempotencyStorage>()
        every { storage.claimTtl } returns 1.minutes
        coEvery { storage.claim(any(), any(), any(), any()) } returns EventClaim.CLAIMED
        coEvery { storage.markProcessed(any(), any(), any()) } throws RuntimeException("cache down")
        coEvery { storage.release(any(), any(), any(), any()) } just Runs
        val fixture = SutFixture(eventIdempotencyStorage = storage)
        val topic = "embedded.idempotency.mark-failure"
        val consumer = fixture.newConsumer()
        val attempts = AtomicInteger(0)
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { attempts.incrementAndGet() }
        consumer.start(backgroundScope)

        whenn()
        fixture.producer.publishEncoded(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        advanceThroughRetries()

        then()
        assertEquals(1, attempts.get())
        assertTrue(fixture.exhaustedEventDataSource.findByOriginalTopic(topic).isEmpty())
        coVerify(exactly = 0) { storage.release(any(), any(), any(), any()) }
    }
}
