package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KeyedTestEvent
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours

internal class EmbeddedEventReplayTest {

    private class SutFixture(val topicQueueHolder: TopicQueueHolder<String> = TopicQueueHolder()) {
        val transactionManager = mockedTransactionManager()
        val exhaustedEventDataSource = InMemoryExhaustedEventDataSource(transactionManager)
        val sut = consumer(GROUP)

        fun consumer(consumerGroup: String) = EmbeddedEventConsumer(
            topicQueueHolder = topicQueueHolder,
            protoBuf = encodedEventProtoBuf,
            exhaustedEventDataSource = exhaustedEventDataSource,
            transactionManager = transactionManager,
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            consumerGroup = consumerGroup,
        )
    }

    private fun exhausted(event: KeyedTestEvent, payload: ByteArray = encodedEventProtoBuf.encodeToByteArray(event)) = DltEvent(
        payload = payload,
        originalTopic = event.topic,
        attempt = DltRetry.DEFAULT_MAX_ATTEMPTS,
        topic = event.topic,
    )

    private suspend fun TestScope.advanceThroughRetries() {
        runCurrent()
        advanceTimeBy(1.hours)
        runCurrent()
    }

    @Test
    fun `should deliver a replayed event to the receiver of its topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = TOPIC)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) { received += it }
        fixture.sut.start(backgroundScope)

        whenn()
        val replayed = fixture.sut.replay(exhausted(event))
        advanceThroughRetries()

        then()
        assertTrue(replayed)
        assertEquals(listOf(event), received.toList())
    }

    @Test
    fun `should not deliver a replayed event to another consumer group of the same topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val otherGroup = fixture.consumer("other_group")
        val otherReceived = AtomicInteger(0)
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) { }
        otherGroup.registerReceiver<KeyedTestEvent, String>(TOPIC) { otherReceived.incrementAndGet() }
        fixture.sut.start(backgroundScope)
        otherGroup.start(backgroundScope)

        whenn()
        fixture.sut.replay(exhausted(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = TOPIC)))
        advanceThroughRetries()

        then()
        assertEquals(0, otherReceived.get())
    }

    @Test
    fun `should give a replayed event every attempt again and park it under a new key when it keeps failing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val attempts = AtomicInteger(0)
        val replayedEvent = exhausted(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = TOPIC))
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) {
            attempts.incrementAndGet()
            throw RuntimeException("boom")
        }
        fixture.sut.start(backgroundScope)

        whenn()
        fixture.sut.replay(replayedEvent)
        advanceThroughRetries()

        then()
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, attempts.get())
        val parked = fixture.exhaustedEventDataSource.findByOriginalTopic(TOPIC).single()
        assertNotEquals(replayedEvent.idempotencyKey, parked.idempotencyKey)
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, parked.attempt)
    }

    @Test
    fun `should not replay an event of a topic it has no receiver for`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) { }
        fixture.sut.start(backgroundScope)

        whenn()
        val replayed = fixture.sut.replay(exhausted(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = "embedded.replay.unknown")))

        then()
        assertFalse(replayed)
    }

    @Test
    fun `should not replay an event before the consumer has started`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val received = AtomicInteger(0)
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) { received.incrementAndGet() }

        whenn()
        val replayed = fixture.sut.replay(exhausted(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = TOPIC)))
        fixture.sut.start(backgroundScope)
        advanceThroughRetries()

        then()
        assertFalse(replayed)
        assertEquals(0, received.get())
    }

    @Test
    fun `should not replay an event whose payload no longer decodes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) { }
        fixture.sut.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = TOPIC)

        whenn()
        val replayed = fixture.sut.replay(exhausted(event, payload = byteArrayOf(0x7f, 0x7f, 0x7f)))

        then()
        assertFalse(replayed)
    }

    private companion object {
        const val TOPIC = "embedded.replay"
        const val GROUP = "appointments_group"
    }
}
