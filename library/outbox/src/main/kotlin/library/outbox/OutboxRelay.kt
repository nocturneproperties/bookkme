package library.outbox

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.EventStreaming.RawRecord
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import library.outbox.datasource.OutboxEvent
import library.outbox.datasource.OutboxEventDataSource
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.StoredPayload
import library.outbox.personal.UnreadableSealedPayload
import library.outbox.personal.readablePayload
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class OutboxRelay(
    private val outboxEventDataSource: OutboxEventDataSource,
    private val transactionManager: TransactionManager,
    private val rawProducer: EventStreaming.RawProducer<String>,
    private val personalDataCipher: PersonalDataCipher,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val idleDelay: Duration = DEFAULT_IDLE_DELAY,
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
        rawProducer.publishAll(events.mapNotNull { it.toPublishedRecord() })
        outboxEventDataSource.delete(events.map(OutboxEvent::id))
        events.size
    }

    private suspend fun relayBatchOrZero(): Int = relayBatch()
        .onFailure { logger.error("Failed to relay outbox events, retrying in {}. Error: {}", idleDelay, it.message) }
        .getOrDefault(0)

    private fun isDrained(relayed: Int) = relayed < batchSize

    private suspend fun OutboxEvent.toPublishedRecord(): RawRecord<String>? {
        val readable = try {
            personalDataCipher.readablePayload(topic, StoredPayload(payload, personalDataEncrypted))
        } catch (unreadable: UnreadableSealedPayload) {
            logger.error("Dropped unreadable outbox event {}, id {}. Error: {}", topic, id, unreadable.message)
            return null
        }
        if (readable == null) {
            logger.info("Dropped outbox event {}, id {}, because its data subject was erased", topic, id)
            return null
        }
        return RawRecord(topic, partitionKey, readable)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        val DEFAULT_IDLE_DELAY = 500.milliseconds
    }
}
