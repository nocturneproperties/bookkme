package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.impl.InMemoryExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.inMemoryEventIdempotencyStorage
import com.bookk.core.data.eventstreaming.registerReceiver
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

internal class KafkaEventConsumerTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
        }
    }

    private class Observations {
        val order = ConcurrentLinkedQueue<Int>()
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)

        fun enter() {
            val current = inFlight.incrementAndGet()
            peakInFlight.updateAndGet { peak -> maxOf(peak, current) }
        }

        fun exit() = inFlight.decrementAndGet()
    }

    private fun consumerFor(topic: String, observations: Observations, latch: CountDownLatch): Job {
        val consumer = KafkaEventConsumer(
            servers = KafkaTestBroker.servers,
            consumerGroup = "consumer-test-${Uuid.random()}",
            eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
            protoBuf = KafkaTestBroker.protoBuf,
            dltProducer = NoopProducer(),
            exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        )
        consumer.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            observations.enter()
            Thread.sleep(60)
            observations.order += event.sequence
            observations.exit()
            latch.countDown()
        }
        return consumer.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    private suspend fun publish(topic: String, entityId: String, count: Int) {
        val producer = KafkaEventProducer(KafkaTestBroker.servers, "consumer-test", KafkaTestBroker.protoBuf)
        repeat(count) { producer.send(KeyedTestEvent(entityId, it, topic)) }
    }

    @Test
    fun `should process records of one partition one at a time`() = runIntegrationTest {
        given()
        val topic = "consumer.serial.${Uuid.random()}"
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val observations = Observations()
        val latch = CountDownLatch(6)

        whenn()
        val job = withContext(Dispatchers.IO) {
            publish(topic, entityId = "entity-1", count = 6)
            consumerFor(topic, observations, latch).also {
                latch.await(10, TimeUnit.SECONDS)
            }
        }
        job.cancel()

        then()
        assertEquals(1, observations.peakInFlight.get())
    }

    @Test
    fun `should preserve publication order for one entity`() = runIntegrationTest {
        given()
        val topic = "consumer.order.${Uuid.random()}"
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val observations = Observations()
        val latch = CountDownLatch(6)

        whenn()
        val job = withContext(Dispatchers.IO) {
            publish(topic, entityId = "entity-1", count = 6)
            consumerFor(topic, observations, latch).also {
                latch.await(10, TimeUnit.SECONDS)
            }
        }
        job.cancel()

        then()
        assertEquals((0 until 6).toList(), observations.order.toList())
    }

    @Test
    fun `should deliver every published event to the receiver`() = runIntegrationTest {
        given()
        val topic = "consumer.delivery.${Uuid.random()}"
        KafkaTestBroker.createTopic(topic, partitions = 3)
        val observations = Observations()
        val latch = CountDownLatch(4)

        whenn()
        val job = withContext(Dispatchers.IO) {
            publish(topic, entityId = "entity-1", count = 4)
            consumerFor(topic, observations, latch).also {
                latch.await(10, TimeUnit.SECONDS)
            }
        }
        job.cancel()

        then()
        assertEquals(4, observations.order.size)
    }
}
