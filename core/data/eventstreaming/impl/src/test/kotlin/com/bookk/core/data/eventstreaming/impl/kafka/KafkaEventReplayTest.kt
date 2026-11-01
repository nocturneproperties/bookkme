package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

    private val exhausted = DltEvent(
        payload = byteArrayOf(1, 2, 3),
        originalTopic = TOPIC,
        attempt = DltRetry.DEFAULT_MAX_ATTEMPTS,
        topic = TOPIC,
    )

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
        assertArrayEquals(exhausted.payload, sent.captured.payload)
        assertNotEquals(exhausted.idempotencyKey, sent.captured.idempotencyKey)
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

    private companion object {
        const val TOPIC = "kafka.replay"
        const val GROUP = "appointments_group"
    }
}
