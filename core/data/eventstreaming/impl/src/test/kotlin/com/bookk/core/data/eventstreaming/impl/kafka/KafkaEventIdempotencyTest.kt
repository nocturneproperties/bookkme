package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

internal class KafkaEventIdempotencyTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
        }
    }

    private class SutFixture {
        val eventIdempotencyStorage: EventIdempotencyStorage = inMemoryEventIdempotencyStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val producer = KafkaEventProducer(KafkaTestBroker.servers, "idempotency-test-${Uuid.random()}", KafkaTestBroker.protoBuf)

        val transactionManager = mockedTransactionManager()

        fun newConsumer(consumerGroup: String) = KafkaEventConsumer(
            servers = KafkaTestBroker.servers,
            consumerGroup = consumerGroup,
            eventIdempotencyStorage = eventIdempotencyStorage,
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = NoopProducer(),
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource(transactionManager),
            transactionManager = transactionManager,
        )
    }

    @Test
    fun `should handle the same event once in every consumer group sharing the storage`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val topic = "idempotency.fan-out.${Uuid.random()}"
        KafkaTestBroker.createTopic(topic, partitions = 1)
        val userReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val businessReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val latch = CountDownLatch(2)
        val userConsumer = fixture.newConsumer("user-${Uuid.random()}")
        val businessConsumer = fixture.newConsumer("business-${Uuid.random()}")
        userConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> userReceived += event; latch.countDown() }
        businessConsumer.registerReceiver<KeyedTestEvent, String>(topic) { event -> businessReceived += event; latch.countDown() }

        whenn()
        withContext(Dispatchers.IO) {
            fixture.producer.send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
            userConsumer.start(fixture.scope)
            businessConsumer.start(fixture.scope)
            latch.await(10, TimeUnit.SECONDS)
        }
        fixture.scope.cancel()

        then()
        assertEquals(1, userReceived.size)
        assertEquals(1, businessReceived.size)
    }

    @Test
    fun `should handle an event re-published with the same idempotency key only once`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val topic = "idempotency.duplicate.${Uuid.random()}"
        KafkaTestBroker.createTopic(topic, partitions = 1)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        val laterEventArrived = CountDownLatch(1)
        val consumer = fixture.newConsumer("business-${Uuid.random()}")
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            received += event
            if (event.sequence == 1) laterEventArrived.countDown()
        }
        val duplicated = KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic)

        whenn()
        withContext(Dispatchers.IO) {
            fixture.producer.send(duplicated)
            fixture.producer.send(duplicated)
            fixture.producer.send(KeyedTestEvent(entityId = "entity-1", sequence = 1, topic = topic))
            consumer.start(fixture.scope)
            laterEventArrived.await(10, TimeUnit.SECONDS)
        }
        fixture.scope.cancel()

        then()
        assertEquals(listOf(0, 1), received.map { it.sequence })
    }
}
