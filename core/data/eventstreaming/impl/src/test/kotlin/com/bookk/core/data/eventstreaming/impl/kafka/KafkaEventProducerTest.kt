package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.send
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class KafkaEventProducerTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
        }
    }

    private fun producer() = KafkaEventProducer(
        servers = KafkaTestBroker.servers,
        client = "producer-test",
        protoBuf = KafkaTestBroker.protoBuf
    )

    private fun topicNamed() = "producer.test.${Uuid.random()}"

    @Test
    fun `should put the partition key on the produced record`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)

        whenn()
        val records = withContext(Dispatchers.IO) {
            producer().send(event)
            KafkaTestBroker.drain(topic, expected = 1)
        }

        then()
        assertEquals(1, records.size)
        assertEquals("entity-1", records.single().key())
    }

    @Test
    fun `should leave the record key null when the event declares none`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val event = UnkeyedTestEvent(payload = "no key", topic = topic)

        whenn()
        val records = withContext(Dispatchers.IO) {
            producer().send(event)
            KafkaTestBroker.drain(topic, expected = 1)
        }

        then()
        assertEquals(1, records.size)
        assertNull(records.single().key())
    }

    @Test
    fun `should route every event of one entity to a single partition`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)

        whenn()
        val records = withContext(Dispatchers.IO) {
            val eventProducer = producer()
            repeat(12) { eventProducer.send(KeyedTestEvent("entity-1", it, topic)) }
            KafkaTestBroker.drain(topic, expected = 12)
        }

        then()
        assertEquals(12, records.size)
        assertEquals(1, records.map { it.partition() }.toSet().size)
    }

    @Test
    fun `should spread different entities across partitions`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)

        whenn()
        val records = withContext(Dispatchers.IO) {
            val eventProducer = producer()
            repeat(30) { eventProducer.send(KeyedTestEvent("entity-$it", it, topic)) }
            KafkaTestBroker.drain(topic, expected = 30)
        }

        then()
        assertEquals(30, records.size)
        assertTrue(records.map { it.partition() }.toSet().size > 1)
    }

    @Test
    fun `should preserve publication order within one entity partition`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)

        whenn()
        val records = withContext(Dispatchers.IO) {
            val eventProducer = producer()
            repeat(10) { eventProducer.send(KeyedTestEvent("entity-1", it, topic)) }
            KafkaTestBroker.drain(topic, expected = 10)
        }

        then()
        val sequences = records.map { KafkaTestBroker.protoBuf.decodeFromByteArray<KeyedTestEvent>(it.value()).sequence }
        assertEquals((0 until 10).toList(), sequences)
    }

    @Test
    fun `should round trip an event carrying a null optional field`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val event = NullableFieldTestEvent(required = "entity-1", optional = null, topic = topic)

        whenn()
        val records = withContext(Dispatchers.IO) {
            producer().send(event)
            KafkaTestBroker.drain(topic, expected = 1)
        }

        then()
        val decoded = KafkaTestBroker.protoBuf.decodeFromByteArray<NullableFieldTestEvent>(records.single().value())
        assertNull(decoded.optional)
        assertEquals("entity-1", records.single().key())
    }

    @Test
    fun `should round trip an event carrying a present optional field`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val event = NullableFieldTestEvent(required = "entity-1", optional = "+10000000000", topic = topic)

        whenn()
        val records = withContext(Dispatchers.IO) {
            producer().send(event)
            KafkaTestBroker.drain(topic, expected = 1)
        }

        then()
        val decoded = KafkaTestBroker.protoBuf.decodeFromByteArray<NullableFieldTestEvent>(records.single().value())
        assertEquals("+10000000000", decoded.optional)
    }

    @Test
    fun `should publish raw bytes under the given topic and partition key`() = runIntegrationTest {
        given()
        val topic = topicNamed()
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 7, topic = topic)
        val payload = KafkaTestBroker.protoBuf.encodeToByteArray(KeyedTestEvent.serializer(), event)

        whenn()
        val records = withContext(Dispatchers.IO) {
            producer().publish(topic = topic, partitionKey = "entity-1", payload = payload)
            KafkaTestBroker.drain(topic, expected = 1)
        }

        then()
        assertEquals("entity-1", records.single().key())
        assertEquals(event, KafkaTestBroker.protoBuf.decodeFromByteArray<KeyedTestEvent>(records.single().value()))
    }
}
