package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.IdempotentDelivery
import com.bookk.core.data.eventstreaming.impl.logDelivered
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.isActive
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import kotlin.reflect.KType
import kotlin.time.Duration.Companion.milliseconds

class KafkaEventConsumer(
    servers: List<String>,
    private val consumerGroup: String,
    eventIdempotencyStorage: EventIdempotencyStorage,
    private val protoBuf: ProtoBuf,
    private val dltProducer: EventStreaming.Producer<String>,
    private val exhaustedEventDataSource: ExhaustedEventDataSource,
    private val transactionManager: TransactionManager,
    private val maxAttempts: Int = DltRetry.DEFAULT_MAX_ATTEMPTS,
) : EventStreaming.Consumer<String> {

    private val logger = KtorSimpleLogger("KafkaEventConsumer")
    private val idempotentDelivery = IdempotentDelivery(consumerGroup, eventIdempotencyStorage, logger)
    private val receivers = ConcurrentMap<String, suspend (ByteArray, Int) -> Unit>()

    private val consumer = KafkaConsumer(
        mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to servers.joinToString { it },
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroup,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false"
        ),
        StringDeserializer(),
        ByteArrayDeserializer()
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event<String>> registerReceiver(
        topic: String,
        type: KType,
        onEvent: suspend (T) -> Unit
    ): EventStreaming.Consumer<String> {
        receivers[topic] = { rawBytes, attempt ->
            runCatching {
                val serializer = protoBuf.serializersModule.serializer(type)
                protoBuf.decodeFromByteArray(serializer, rawBytes) as T
            }.onFailure {
                logger.debug("Received unprocessable event, Topic: {}. Error: {}", topic, it.message)
            }.onSuccess { event ->
                logger.debug("Received event, Topic: {}. Event: {}", event.topic, event)
                idempotentDelivery.deliver(event, onEvent)
                    .onSuccess { outcome -> logger.logDelivered(event, outcome) }
                    .onFailure { error ->
                        handleFailure(topic, rawBytes, attempt)
                        logger.error("Error while processing event for topic: ${event.topic}. Event: $event, $error")
                    }
            }
        }
        return this
    }

    override suspend fun replay(exhausted: DltEvent): Boolean {
        if (exhausted.originalTopic !in receivers) return false
        dltProducer.send(
            DltEvent(
                payload = exhausted.payload,
                originalTopic = exhausted.originalTopic,
                topic = DltRetry.dltTopic(exhausted.originalTopic, consumerGroup)
            )
        )
        return true
    }

    override fun start(scope: CoroutineScope): Job {
        val originalTopics = receivers.keys.toList()
        originalTopics.forEach { topic ->
            receivers[DltRetry.dltTopic(topic, consumerGroup)] = { rawBytes, _ -> retryFromDlt(rawBytes) }
        }
        consumer.subscribe(receivers.keys)
        return flow {
            while (currentCoroutineContext().isActive) {
                val records = consumer.poll(Duration.ofSeconds(1))
                if (!records.isEmpty) {
                    emit(records)
                }
            }
        }
            .onEach { records ->
                coroutineScope {
                    records.partitions()
                        .map { partition -> async { processPartition(records.records(partition)) } }
                        .awaitAll()
                    consumer.commitSync()
                }
            }
            .retry()
            .launchIn(scope)
    }

    private suspend fun handleFailure(topic: String, rawBytes: ByteArray, attempt: Int) {
        val nextAttempt = attempt + 1
        val dltEvent = DltEvent(payload = rawBytes, originalTopic = topic, attempt = nextAttempt, topic = DltRetry.dltTopic(topic, consumerGroup))
        if (DltRetry.isExhausted(nextAttempt, maxAttempts)) {
            persistOrRequeue(dltEvent)
        } else {
            dltProducer.send(dltEvent)
        }
    }

    private suspend fun retryFromDlt(rawBytes: ByteArray) {
        runCatching { protoBuf.decodeFromByteArray(DltEvent.serializer(), rawBytes) }
            .onSuccess { dltEvent -> route(dltEvent) }
            .onFailure { logger.error("Failed to decode dlt event: {}", it.message) }
    }

    private suspend fun route(dltEvent: DltEvent) {
        delay(DltRetry.backoffMillis(dltEvent.attempt).milliseconds)
        if (DltRetry.isExhausted(dltEvent.attempt, maxAttempts)) {
            persistOrRequeue(dltEvent)
        } else {
            receivers[dltEvent.originalTopic]?.invoke(dltEvent.payload, dltEvent.attempt)
        }
    }

    private suspend fun persistOrRequeue(dltEvent: DltEvent) {
        transactionManager.transaction { exhaustedEventDataSource.save(dltEvent) }
            .onFailure {
                logger.error("Failed to persist exhausted event for topic: {}. Retrying. Error: {}", dltEvent.originalTopic, it.message)
                dltProducer.send(dltEvent.copy(attempt = dltEvent.attempt + 1))
            }
    }

    private suspend fun processPartition(records: List<ConsumerRecord<String, ByteArray>>) {
        records.forEach { record -> receivers[record.topic()]?.invoke(record.value(), 0) }
    }
}
