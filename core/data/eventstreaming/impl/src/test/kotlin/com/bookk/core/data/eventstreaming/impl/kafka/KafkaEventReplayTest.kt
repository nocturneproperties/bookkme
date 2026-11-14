package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.EventStreaming
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
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class KafkaEventReplayTest {

    private class SutFixture {
        val transactionManager = mockedTransactionManager()
        val dltProducer = mockk<EventStreaming.Producer<String>>()
        val sut = KafkaEventConsumer(
            servers = listOf("localhost:9092"),
            consumerGroup = GROUP,
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = dltProducer,
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource(transactionManager),
            transactionManager = transactionManager,
        )
    }

    private val readable = ParkedPayload.Readable(byteArrayOf(1, 2, 3))
    private val exhausted = ExhaustedEvent.stub(originalTopic = TOPIC, payload = readable)

    @Test
    fun `should send a replayed event to its consumer group's dlt topic with every attempt left`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val sent = slot<DltEvent>()
        coEvery { fixture.dltProducer.send(capture(sent), any()) } just Runs
        fixture.sut.registerReceiver<KeyedTestEvent, String>(TOPIC) { }

        whenn()
        val replayed = fixture.sut.replay(exhausted)

        then()
        assertTrue(replayed)
        assertEquals(DltRetry.dltTopic(TOPIC, GROUP), sent.captured.topic)
        assertEquals(TOPIC, sent.captured.originalTopic)
        assertEquals(0, sent.captured.attempt)
        assertArrayEquals(readable.bytes, sent.captured.payload)
    }

    @Test
    fun `should not replay an event of a topic it has no receiver for`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.registerReceiver<KeyedTestEvent, String>("kafka.replay.other") { }

        whenn()
        val replayed = fixture.sut.replay(exhausted)

        then()
        assertFalse(replayed)
        coVerify(exactly = 0) { fixture.dltProducer.send(any<DltEvent>(), any()) }
    }

    @Test
    fun `should send the readable payload of a replayed personal event to the dlt topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val sent = slot<DltEvent>()
        coEvery { fixture.dltProducer.send(capture(sent), any()) } just Runs
        fixture.sut.registerReceiver<PersonalTestEvent, String>(TOPIC) { }
        val event = PersonalTestEvent(userId = Uuid.random(), name = "Alice", topic = TOPIC)
        val payload = ParkedPayload.Readable(KafkaTestBroker.protoBuf.encodeToByteArray(event))

        whenn()
        val replayed = fixture.sut.replay(ExhaustedEvent.stub(originalTopic = TOPIC, payload = payload))

        then()
        assertTrue(replayed)
        assertEquals(event, KafkaTestBroker.protoBuf.decodeFromByteArray<PersonalTestEvent>(sent.captured.payload))
    }

    @Test
    fun `should drop a replayed event whose data subject was erased`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.registerReceiver<PersonalTestEvent, String>(TOPIC) { }
        val erased = ExhaustedEvent.stub(originalTopic = TOPIC, payload = ParkedPayload.SubjectErased)

        whenn()
        val replayed = fixture.sut.replay(erased)

        then()
        assertTrue(replayed)
        coVerify(exactly = 0) { fixture.dltProducer.send(any<DltEvent>(), any()) }
    }

    @Test
    fun `should not replay an event whose parked payload cannot be opened`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.registerReceiver<PersonalTestEvent, String>(TOPIC) { }
        val unreadable = ExhaustedEvent.stub(originalTopic = TOPIC, payload = ParkedPayload.Unreadable("tampered"))

        whenn()
        val replayed = fixture.sut.replay(unreadable)

        then()
        assertFalse(replayed)
        coVerify(exactly = 0) { fixture.dltProducer.send(any<DltEvent>(), any()) }
    }

    private companion object {
        const val TOPIC = "kafka.replay"
        const val GROUP = "appointments_group"
    }
}
