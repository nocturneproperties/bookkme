package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.DltRetry
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.EventStreaming.Consumer
import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.impl.IdempotentDelivery
import com.bookk.core.data.eventstreaming.impl.logDelivered
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.runCatchingUnlessCancelled
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.time.Duration.Companion.milliseconds

private typealias Receiver = suspend (event: Event<String>, attempt: Int) -> Unit

class EmbeddedEventConsumer(
    private val topicQueueHolder: TopicQueueHolder<String>,
    private val protoBuf: ProtoBuf,
    private val exhaustedEventDataSource: ExhaustedEventDataSource,
    private val transactionManager: TransactionManager,
    eventIdempotencyStorage: EventIdempotencyStorage,
    private val consumerGroup: String,
    private val maxAttempts: Int = DltRetry.DEFAULT_MAX_ATTEMPTS,
) : Consumer<String> {

    private val logger = KtorSimpleLogger("EmbeddedEventConsumer")
    private val idempotentDelivery = IdempotentDelivery(consumerGroup, eventIdempotencyStorage, logger)
    private val receivers = ConcurrentMap<String, Receiver>()
    private val types = ConcurrentMap<String, KType>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event<String>> registerReceiver(
        topic: String,
        type: KType,
        onEvent: suspend (T) -> Unit
    ): Consumer<String> {
        types[topic] = type
        receivers[topic] = { event, attempt -> deliver(topic, event as T, attempt, onEvent) }
        return this
    }

    override suspend fun replay(exhausted: DltEvent): Boolean {
        val dltTopic = DltRetry.dltTopic(exhausted.originalTopic, consumerGroup)
        val type = types[exhausted.originalTopic]
        if (type == null || dltTopic !in receivers) return false
        val event = runCatching { decode(type, exhausted.payload) }
            .onFailure { logger.error("Cannot replay an undecodable event, topic: {}. Error: {}", exhausted.originalTopic, it.message) }
            .getOrNull() ?: return false
        topicQueueHolder.publish(EmbeddedDltEvent(originalEvent = event, attempt = FIRST_ATTEMPT, topic = dltTopic))
        return true
    }

    override fun start(scope: CoroutineScope): Job {
        registerDltReceivers()
        return receivers.keys
            .map(::eventsOf)
            .merge()
            .launchIn(scope)
    }

    private fun registerDltReceivers() {
        receivers.keys.toList().forEach { topic ->
            receivers[DltRetry.dltTopic(topic, consumerGroup)] = { event, _ -> retryFromDlt(event as EmbeddedDltEvent) }
        }
    }

    private fun eventsOf(topic: String): Flow<Event<String>> =
        topicQueueHolder.subscribe(topic)
            .receiveAsFlow()
            .onEach { event ->
                logger.debug("Event received: topic:{}, event:{}", event.topic, event)
                dispatch(event)
            }

    private suspend fun dispatch(event: Event<String>) {
        runCatchingUnlessCancelled { decoded(event)?.let { receivers[event.topic]?.invoke(it, FIRST_ATTEMPT) } }
            .onFailure { logger.error("Failed to handle: topic:{}, event:{}, error:{}", event.topic, event, it.stackTraceToString()) }
    }

    private suspend fun <T : Event<String>> deliver(topic: String, event: T, attempt: Int, onEvent: suspend (T) -> Unit) {
        idempotentDelivery.deliver(event, onEvent)
            .onSuccess { outcome -> logger.logDelivered(event, outcome) }
            .onFailure {
                handleFailure(topic, event, attempt)
                logger.error("Error while processing event for topic: ${event.topic}. Event: $event")
            }
    }

    private suspend fun handleFailure(topic: String, event: Event<String>, attempt: Int) {
        val dltEvent = EmbeddedDltEvent(originalEvent = event, attempt = attempt + 1, topic = DltRetry.dltTopic(topic, consumerGroup))
        if (dltEvent.isExhausted()) persistOrRequeue(dltEvent) else topicQueueHolder.publish(dltEvent)
    }

    private suspend fun retryFromDlt(dltEvent: EmbeddedDltEvent) {
        delay(DltRetry.backoffMillis(dltEvent.attempt).milliseconds)
        if (dltEvent.isExhausted()) {
            persistOrRequeue(dltEvent)
        } else {
            receivers[dltEvent.originalEvent.topic]?.invoke(dltEvent.originalEvent, dltEvent.attempt)
        }
    }

    private suspend fun persistOrRequeue(dltEvent: EmbeddedDltEvent) {
        val originalEvent = dltEvent.originalEvent
        val type = types[originalEvent.topic] ?: return
        val record = DltEvent(
            payload = encode(type, originalEvent),
            originalTopic = originalEvent.topic,
            attempt = dltEvent.attempt,
            topic = originalEvent.topic,
            idempotencyKey = dltEvent.idempotencyKey
        )
        transactionManager.transaction { exhaustedEventDataSource.save(record) }
            .onFailure {
                logger.error("Failed to persist exhausted event for topic: {}. Retrying. Error: {}", originalEvent.topic, it.message)
                topicQueueHolder.publish(dltEvent.copy(attempt = dltEvent.attempt + 1))
            }
    }

    private fun EmbeddedDltEvent.isExhausted() = DltRetry.isExhausted(attempt, maxAttempts)

    private fun decoded(event: Event<String>): Event<String>? = when (event) {
        is EmbeddedEncodedEvent -> decode(event)
        else -> event
    }

    private fun decode(event: EmbeddedEncodedEvent): Event<String>? {
        val type = types[event.topic] ?: return null
        return runCatching { decode(type, event.payload) }
            .onFailure { logger.debug("Received unprocessable event, Topic: {}. Error: {}", event.topic, it.stackTraceToString()) }
            .getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(type: KType, payload: ByteArray): Event<String> =
        protoBuf.decodeFromByteArray(protoBuf.serializersModule.serializer(type), payload) as Event<String>

    private fun encode(type: KType, event: Event<String>): ByteArray =
        protoBuf.encodeToByteArray(protoBuf.serializersModule.serializer(type), event)

    private companion object {
        const val FIRST_ATTEMPT = 0
    }
}
