package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.FlakyExhaustedEventDataSource
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

internal class KafkaDltRetryTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
        }
    }

    private fun producer() = KafkaEventProducer(KafkaTestBroker.servers, "dlt-retry-test-${Uuid.random()}", KafkaTestBroker.protoBuf)

    private fun consumerGroupFor(topic: String) = "$topic-group"

    private fun prepareTopics(topic: String, consumerGroup: String = consumerGroupFor(topic)) {
        KafkaTestBroker.createTopic(topic, partitions = 1)
        KafkaTestBroker.createTopic(DltRetry.dltTopic(topic, consumerGroup), partitions = 1)
    }

    private fun consumer(
        topic: String,
        exhaustedEventDataSource: ExhaustedEventDataSource = InMemoryExhaustedEventDataSource(),
    ) = KafkaEventConsumer(
        servers = KafkaTestBroker.servers,
        consumerGroup = consumerGroupFor(topic),
        eventIdempotencyStorage = inMemoryEventIdempotencyStorage(),
        protoBuf = KafkaTestBroker.protoBuf,
        dltProducer = producer(),
        exhaustedEventDataSource = exhaustedEventDataSource,
    )

    private suspend fun awaitUntil(deadlineMillis: Long = 20_000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + deadlineMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }

    @Test
    fun `should retry a failing event through the dlt topic until it succeeds`() = runIntegrationTest {
        given()
        val topic = "dlt.retry.success.${Uuid.random()}"
        prepareTopics(topic)
        val attempts = AtomicInteger(0)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        val latch = CountDownLatch(1)
        val sut = consumer(topic)
        sut.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            if (attempts.getAndIncrement() < 2) throw RuntimeException("boom") else {
                received += event
                latch.countDown()
            }
        }
        var job: Job? = null

        whenn()
        withContext(Dispatchers.IO) {
            producer().send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
            job = sut.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
            latch.await(20, TimeUnit.SECONDS)
        }

        then()
        assertEquals(1, received.size)
        assertEquals(3, attempts.get())
        job?.cancel()
    }

    @Test
    fun `should persist an always-failing event to the exhausted store after max attempts`() = runIntegrationTest {
        given()
        val topic = "dlt.retry.exhausted.${Uuid.random()}"
        prepareTopics(topic)
        val attempts = AtomicInteger(0)
        val exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        val sut = consumer(topic, exhaustedEventDataSource)
        sut.registerReceiver<KeyedTestEvent, String>(topic) {
            attempts.incrementAndGet()
            throw RuntimeException("boom")
        }
        var job: Job? = null

        whenn()
        withContext(Dispatchers.IO) {
            producer().send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
            job = sut.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
            awaitUntil { exhaustedEventDataSource.findByOriginalTopic(topic).isNotEmpty() }
        }

        then()
        val exhausted = exhaustedEventDataSource.findByOriginalTopic(topic)
        assertEquals(1, exhausted.size)
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, attempts.get())
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, exhausted.single().attempt)
        job?.cancel()
    }

    @Test
    fun `should retry persisting without re-invoking the business handler when the save fails`() = runIntegrationTest {
        given()
        val topic = "dlt.retry.persist.${Uuid.random()}"
        prepareTopics(topic)
        val attempts = AtomicInteger(0)
        val flakyStore = FlakyExhaustedEventDataSource(failuresBeforeSuccess = 2)
        val sut = consumer(topic, flakyStore)
        sut.registerReceiver<KeyedTestEvent, String>(topic) {
            attempts.incrementAndGet()
            throw RuntimeException("boom")
        }
        var job: Job? = null

        whenn()
        withContext(Dispatchers.IO) {
            producer().send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
            job = sut.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
            awaitUntil { flakyStore.findByOriginalTopic(topic).isNotEmpty() }
        }

        then()
        assertEquals(DltRetry.DEFAULT_MAX_ATTEMPTS, attempts.get())
        assertTrue(flakyStore.findByOriginalTopic(topic).isNotEmpty())
        job?.cancel()
    }

    @Test
    fun `should isolate retries between two topics on the same consumer`() = runIntegrationTest {
        given()
        val flakyTopic = "dlt.retry.isolation.flaky.${Uuid.random()}"
        val healthyTopic = "dlt.retry.isolation.healthy.${Uuid.random()}"
        prepareTopics(flakyTopic)
        prepareTopics(healthyTopic, consumerGroupFor(flakyTopic))
        val flakyAttempts = AtomicInteger(0)
        val flakyReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val healthyReceived = ConcurrentLinkedQueue<KeyedTestEvent>()
        val exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        val sut = consumer(flakyTopic, exhaustedEventDataSource)
        sut.registerReceiver<KeyedTestEvent, String>(flakyTopic) { event ->
            if (flakyAttempts.getAndIncrement() < 2) throw RuntimeException("boom") else flakyReceived += event
        }
        sut.registerReceiver<KeyedTestEvent, String>(healthyTopic) { event -> healthyReceived += event }
        var job: Job? = null

        whenn()
        withContext(Dispatchers.IO) {
            producer().send(KeyedTestEvent(entityId = "entity-flaky", sequence = 0, topic = flakyTopic))
            producer().send(KeyedTestEvent(entityId = "entity-healthy", sequence = 0, topic = healthyTopic))
            job = sut.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
            awaitUntil { flakyReceived.isNotEmpty() && healthyReceived.isNotEmpty() }
        }

        then()
        assertEquals(1, healthyReceived.size)
        assertEquals(1, flakyReceived.size)
        assertEquals(3, flakyAttempts.get())
        assertTrue(exhaustedEventDataSource.findByOriginalTopic(flakyTopic).isEmpty())
        assertTrue(exhaustedEventDataSource.findByOriginalTopic(healthyTopic).isEmpty())
        job?.cancel()
    }

    @Test
    fun `should replay a persisted exhausted event back onto its original topic`() = runIntegrationTest {
        given()
        val topic = "dlt.retry.replay.${Uuid.random()}"
        prepareTopics(topic)
        val shouldFail = AtomicInteger(1)
        val received = ConcurrentLinkedQueue<KeyedTestEvent>()
        val exhaustedEventDataSource = InMemoryExhaustedEventDataSource()
        val sut = consumer(topic, exhaustedEventDataSource)
        sut.registerReceiver<KeyedTestEvent, String>(topic) { event ->
            if (shouldFail.get() == 1) throw RuntimeException("boom") else received += event
        }
        var job: Job? = null
        withContext(Dispatchers.IO) {
            producer().send(KeyedTestEvent(entityId = "entity-1", sequence = 0, topic = topic))
            job = sut.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
            awaitUntil { exhaustedEventDataSource.findByOriginalTopic(topic).isNotEmpty() }
        }
        shouldFail.set(0)

        whenn()
        withContext(Dispatchers.IO) {
            producer().replay(exhaustedEventDataSource.findByOriginalTopic(topic).single())
            awaitUntil { received.isNotEmpty() }
        }

        then()
        assertEquals(1, received.size)
        assertEquals("entity-1", received.single().entityId)
        job?.cancel()
    }
}
