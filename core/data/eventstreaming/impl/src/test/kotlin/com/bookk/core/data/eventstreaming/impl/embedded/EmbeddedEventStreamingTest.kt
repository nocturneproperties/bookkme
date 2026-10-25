package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.kafka.KeyedTestEvent
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

internal class EmbeddedEventStreamingTest {

    private class SutFixture {
        val topicQueueHolder = TopicQueueHolder<String>()
        val protoBuf = ProtoBuf { encodeDefaults = true }
        val producer = EmbeddedEventProducer(topicQueueHolder, protoBuf)
        val eventIdempotencyStorage = inMemoryEventIdempotencyStorage()

        fun encode(event: KeyedTestEvent) = protoBuf.encodeToByteArray(KeyedTestEvent.serializer(), event)
    }

    private fun SutFixture.newConsumer(consumerGroup: String = "business_group") = EmbeddedEventConsumer(
        topicQueueHolder = topicQueueHolder,
        protoBuf = protoBuf,
        exhaustedEventDataSource = InMemoryExhaustedEventDataSource(),
        eventIdempotencyStorage = eventIdempotencyStorage,
        consumerGroup = consumerGroup,
    )

    @Test
    fun `should deliver published event to its subscriber`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "topic.single"
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> received += event }
        consumer.start(backgroundScope)

        whenn()
        fixture.producer.send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        runCurrent()

        then()
        assertEquals(1, received.size)
    }

    @Test
    fun `should fan out published event to every subscriber of the same topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "topic.shared"
        val firstServiceConsumer = fixture.newConsumer(consumerGroup = "user_group")
        val secondServiceConsumer = fixture.newConsumer(consumerGroup = "business_group")
        val firstReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val secondReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        firstServiceConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> firstReceived += event }
        secondServiceConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> secondReceived += event }
        firstServiceConsumer.start(backgroundScope)
        secondServiceConsumer.start(backgroundScope)

        whenn()
        fixture.producer.send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
        runCurrent()

        then()
        assertEquals(1, firstReceived.size)
        assertEquals(1, secondReceived.size)
    }

    @Test
    fun `should not deliver event to a consumer subscribed to a different topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>("topic.interested") { event -> received += event }
        consumer.start(backgroundScope)

        whenn()
        fixture.producer.send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = "topic.other"))
        runCurrent()

        then()
        assertTrue(received.isEmpty())
    }

    @Test
    fun `should deliver a raw published event decoded to its subscriber`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "topic.raw"
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> received += event }
        consumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 7, topic = topic)

        whenn()
        fixture.producer.publish(topic = topic, partitionKey = "entity-1", payload = fixture.encode(event))
        runCurrent()

        then()
        assertEquals(listOf(event), received.toList())
    }

    @Test
    fun `should decode a raw published event for every subscriber of the same topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "topic.raw.shared"
        val firstServiceConsumer = fixture.newConsumer(consumerGroup = "user_group")
        val secondServiceConsumer = fixture.newConsumer(consumerGroup = "business_group")
        val firstReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val secondReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        firstServiceConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> firstReceived += event }
        secondServiceConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> secondReceived += event }
        firstServiceConsumer.start(backgroundScope)
        secondServiceConsumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 7, topic = topic)

        whenn()
        fixture.producer.publish(topic = topic, partitionKey = "entity-1", payload = fixture.encode(event))
        runCurrent()

        then()
        assertEquals(listOf(event), firstReceived.toList())
        assertEquals(listOf(event), secondReceived.toList())
    }

    @Test
    fun `should drop a raw published event it cannot decode and keep delivering later events`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val topic = "topic.raw.corrupt"
        val consumer = fixture.newConsumer()
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> received += event }
        consumer.start(backgroundScope)
        val event = KeyedTestEvent(entityId = "entity-1", sequence = 7, topic = topic)

        whenn()
        fixture.producer.publish(topic = topic, partitionKey = null, payload = byteArrayOf(-1, -1, -1))
        fixture.producer.publish(topic = topic, partitionKey = "entity-1", payload = fixture.encode(event))
        runCurrent()

        then()
        assertEquals(listOf(event), received.toList())
    }
}
