package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.coroutines.ticks
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ExhaustedEventReplayerImpl(
    private val consumer: StandardEventConsumer,
    private val exhaustedEventDataSource: ExhaustedEventDataSource,
    private val transactionManager: TransactionManager,
    meterRegistry: MeterRegistry,
    private val consumerGroup: String,
    private val interval: Duration = DEFAULT_INTERVAL,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) : ExhaustedEventReplayer {

    private val logger = KtorSimpleLogger("ExhaustedEventReplayer")
    private val parkedEvents = MultiGauge.builder(PARKED_EVENTS_METRIC)
        .description("Events that failed every delivery attempt and wait in exhausted_event")
        .register(meterRegistry)

    override fun start(scope: CoroutineScope): Job = ticks(interval)
        .runningFold(false) { parkedRequested, _ -> parkedRequested || requestReplayOfParked() }
        .drop(1)
        .onEach {
            replayRequested()
            exportParkedEvents()
        }
        .launchIn(scope)

    private suspend fun requestReplayOfParked(): Boolean {
        return transactionManager.transaction {
            exhaustedEventDataSource.requestReplayOfAll()
        }.onFailure {
            logger.error("Failed to request a replay of the events parked by {}, retrying in {}. Error: {}", consumerGroup, interval, it.message)
        }.isSuccess
    }

    private suspend fun replayRequested() {
        do {
            val locked = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) { replayBatch() }
                .onFailure { logger.error("Failed to replay the events parked by {}, retrying in {}. Error: {}", consumerGroup, interval, it.message) }
                .getOrDefault(0)
        } while (locked == batchSize)
    }

    private suspend fun replayBatch(): Int {
        val requested = exhaustedEventDataSource.lockReplayRequested(batchSize)
        val (replayed, unreplayable) = requested.partition { consumer.replay(it.event) }
        exhaustedEventDataSource.delete(replayed.map(ExhaustedEvent::id))
        exhaustedEventDataSource.clearReplayRequest(unreplayable.map(ExhaustedEvent::id))
        replayed.forEach { logger.info("Replayed a parked event, topic: {}, consumer group: {}", it.event.originalTopic, consumerGroup) }
        unreplayable.forEach { logger.warn("Cannot replay a parked event, kept in exhausted_event, id: {}, topic: {}, consumer group: {}", it.id, it.event.originalTopic, consumerGroup) }
        return requested.size
    }

    private suspend fun exportParkedEvents() {
        transactionManager.transaction { exhaustedEventDataSource.countByOriginalTopic() }
            .onSuccess { counts -> parkedEvents.register(counts.map { (topic, count) -> parkedRow(topic, count) }, true) }
            .onFailure { logger.error("Failed to count the events parked by {}. Error: {}", consumerGroup, it.message) }
    }

    private fun parkedRow(topic: String, count: Long) = MultiGauge.Row.of(
        Tags.of(CONSUMER_GROUP_TAG, consumerGroup, TOPIC_TAG, topic),
        count
    )

    companion object {
        val DEFAULT_INTERVAL = 1.minutes
        const val DEFAULT_BATCH_SIZE = 100
        const val PARKED_EVENTS_METRIC = "events.exhausted"
        private const val CONSUMER_GROUP_TAG = "consumer_group"
        private const val TOPIC_TAG = "topic"
    }
}
