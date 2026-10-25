package com.bookk.core.data.eventstreaming.data.outbox

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.EventStreaming.RawRecord
import com.bookk.core.data.eventstreaming.OutboxEvent
import com.bookk.core.data.eventstreaming.OutboxEventDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class OutboxRelay(
    private val outboxEventDataSource: OutboxEventDataSource,
    private val transactionManager: TransactionManager,
    private val rawProducer: EventStreaming.RawProducer<String>,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val idleDelay: Duration = DEFAULT_IDLE_DELAY
) : EventHandler {

    private val logger = KtorSimpleLogger("OutboxRelay")

    override fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val relayed = relayBatchOrZero()
                if (isDrained(relayed)) delay(idleDelay)
            }
        }
    }

    suspend fun relayBatch(): Result<Int> = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) {
        val events = outboxEventDataSource.lockPending(batchSize)
        rawProducer.publishAll(events.map { it.toRawRecord() })
        outboxEventDataSource.delete(events.map(OutboxEvent::id))
        events.size
    }

    private suspend fun relayBatchOrZero(): Int = relayBatch()
        .onFailure { logger.error("Failed to relay outbox events, retrying in {}. Error: {}", idleDelay, it.message) }
        .getOrDefault(0)

    private fun isDrained(relayed: Int) = relayed < batchSize

    private fun OutboxEvent.toRawRecord() = RawRecord(topic, partitionKey, payload)

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        val DEFAULT_IDLE_DELAY = 500.milliseconds
    }
}
