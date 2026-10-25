package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.EventStreaming.RawRecord
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KafkaEventProducerBatchTest {

    private class SutFixture {
        val mockProducer = MockProducer(false, null, StringSerializer(), ByteArraySerializer())
        val sut = KafkaEventProducer(mockProducer, KafkaTestBroker.protoBuf)
        val records = (0 until 3).map { RawRecord("topic.batch", "entity-1", byteArrayOf(it.toByte())) }
    }

    @Test
    fun `should send every record of a batch before waiting for the first acknowledgement`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val publishing = async { fixture.sut.publishAll(fixture.records) }
        runCurrent()

        then()
        assertEquals(listOf<Byte>(0, 1, 2), fixture.mockProducer.history().map { it.value().single() })
        assertFalse(publishing.isCompleted)
        repeat(3) { fixture.mockProducer.completeNext() }
        publishing.await()
    }

    @Test
    fun `should fail the batch when a record is rejected by the broker`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val publishing = async { runCatching { fixture.sut.publishAll(fixture.records) } }
        runCurrent()

        whenn()
        fixture.mockProducer.completeNext()
        fixture.mockProducer.errorNext(RuntimeException("broker rejected"))
        fixture.mockProducer.completeNext()

        then()
        assertTrue(publishing.await().exceptionOrNull() is RuntimeException)
    }
}
