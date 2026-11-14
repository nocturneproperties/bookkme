package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.ParkedPayload
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.PersonalTestEvent
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

internal class EmbeddedParkedEventTest {

    private class SutFixture {
        val transactionManager = mockedTransactionManager()
        val exhaustedEventDataSource = InMemoryExhaustedEventDataSource(transactionManager)
        val topicQueueHolder = TopicQueueHolder<String>()
        val producer = EmbeddedEventProducer(topicQueueHolder)
        val received = ConcurrentLinkedQueue<PersonalTestEvent>()
        val sut = EmbeddedEventConsumer(
            topicQueueHolder = topicQueueHolder,
            protoBuf = encodedEventProtoBuf,
            exhaustedEventDataSource = exhaustedEventDataSource,
            transactionManager = transactionManager,
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            consumerGroup = GROUP,
        )

        fun receiveSuccessfully() {
            sut.registerReceiver<PersonalTestEvent, String>(TOPIC) { received += it }
        }

        fun failEveryDelivery() {
            sut.registerReceiver<PersonalTestEvent, String>(TOPIC) { throw RuntimeException("boom") }
        }
    }

    private suspend fun TestScope.advanceThroughRetries() {
        runCurrent()
        advanceTimeBy(1.hours)
        runCurrent()
    }

    @Test
    fun `should hand a failing event to the exhausted event store with its plaintext payload`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.failEveryDelivery()
        fixture.sut.start(backgroundScope)
        val event = PersonalTestEvent(userId = Uuid.random(), name = "Alice", topic = TOPIC)

        whenn()
        fixture.producer.publishEncoded(event)
        advanceThroughRetries()

        then()
        val parked = fixture.exhaustedEventDataSource.findByOriginalTopic(TOPIC).single()
        assertEquals(event, encodedEventProtoBuf.decodeFromByteArray<PersonalTestEvent>(parked.payload))
    }

    @Test
    fun `should not park a failing event the store dropped because its data subject was erased`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.failEveryDelivery()
        fixture.sut.start(backgroundScope)
        val userId = Uuid.random()
        fixture.exhaustedEventDataSource.erase(DataSubject.User(userId))

        whenn()
        fixture.producer.publishEncoded(PersonalTestEvent(userId = userId, name = "Alice", topic = TOPIC))
        advanceThroughRetries()

        then()
        assertTrue(fixture.exhaustedEventDataSource.parked().isEmpty())
    }

    @Test
    fun `should deliver a replayed event whose parked payload is readable`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.receiveSuccessfully()
        fixture.sut.start(backgroundScope)
        val event = PersonalTestEvent(userId = Uuid.random(), name = "Alice", topic = TOPIC)
        val readable = ParkedPayload.Readable(encodedEventProtoBuf.encodeToByteArray(event))

        whenn()
        val replayed = fixture.sut.replay(ExhaustedEvent.stub(originalTopic = TOPIC, payload = readable))
        advanceThroughRetries()

        then()
        assertTrue(replayed)
        assertEquals(listOf(event), fixture.received.toList())
    }

    @Test
    fun `should drop a replayed event whose data subject was erased`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.receiveSuccessfully()
        fixture.sut.start(backgroundScope)
        val erased = ExhaustedEvent.stub(originalTopic = TOPIC, payload = ParkedPayload.SubjectErased)

        whenn()
        val replayed = fixture.sut.replay(erased)
        advanceThroughRetries()

        then()
        assertTrue(replayed)
        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun `should not replay an event whose parked payload cannot be opened`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.receiveSuccessfully()
        fixture.sut.start(backgroundScope)
        val unreadable = ExhaustedEvent.stub(originalTopic = TOPIC, payload = ParkedPayload.Unreadable("tampered"))

        whenn()
        val replayed = fixture.sut.replay(unreadable)
        advanceThroughRetries()

        then()
        assertFalse(replayed)
        assertTrue(fixture.received.isEmpty())
    }

    private companion object {
        const val TOPIC = "embedded.parked"
        const val GROUP = "notifications_group"
    }
}
