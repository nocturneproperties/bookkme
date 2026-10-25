package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.EventClaim
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.domain.entity.runSuspendCatching
import io.ktor.util.logging.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class EventClaimedByUnfinishedDelivery(topic: String, idempotencyKey: String) :
    IllegalStateException("Event is claimed by an unfinished delivery, topic: $topic, idempotencyKey: $idempotencyKey")

internal enum class DeliveryOutcome {
    HANDLED,
    SKIPPED_DUPLICATE
}

internal fun Logger.logDelivered(event: Event<String>, outcome: DeliveryOutcome) = when (outcome) {
    DeliveryOutcome.HANDLED -> debug("Event successfully processed for topic: {}. Event: {}", event.topic, event)
    DeliveryOutcome.SKIPPED_DUPLICATE -> debug("Skipping already processed event, topic: {}, idempotencyKey: {}", event.topic, event.idempotencyKey)
}

internal class IdempotentDelivery(
    private val consumerGroup: String,
    private val eventIdempotencyStorage: EventIdempotencyStorage,
    private val logger: Logger,
) {

    suspend fun <T : Event<String>> deliver(event: T, onEvent: suspend (T) -> Unit): Result<DeliveryOutcome> {
        val deliveryId = Uuid.random().toString()
        val claim = runSuspendCatching { awaitSettledClaim(event, deliveryId) }
            .getOrElse { return Result.failure(it) }
        return when (claim) {
            EventClaim.CLAIMED -> handleClaimed(event, deliveryId, onEvent)
            EventClaim.IN_PROGRESS -> Result.failure(EventClaimedByUnfinishedDelivery(event.topic, event.idempotencyKey))
            EventClaim.ALREADY_PROCESSED -> Result.success(DeliveryOutcome.SKIPPED_DUPLICATE)
        }
    }

    private suspend fun awaitSettledClaim(event: Event<String>, deliveryId: String): EventClaim {
        var claim = claim(event, deliveryId)
        repeat(claimPollsWithinTtl()) {
            if (claim != EventClaim.IN_PROGRESS) return claim
            delay(CLAIM_POLL_INTERVAL)
            claim = claim(event, deliveryId)
        }
        return claim
    }

    private fun claimPollsWithinTtl(): Int = (eventIdempotencyStorage.claimTtl / CLAIM_POLL_INTERVAL).toInt() + 1

    private suspend fun claim(event: Event<String>, deliveryId: String): EventClaim =
        eventIdempotencyStorage.claim(consumerGroup, event.topic, event.idempotencyKey, deliveryId)

    private suspend fun <T : Event<String>> handleClaimed(event: T, deliveryId: String, onEvent: suspend (T) -> Unit): Result<DeliveryOutcome> {
        val handled = try {
            runSuspendCatching { onEvent(event) }
        } catch (e: CancellationException) {
            release(event, deliveryId)
            currentCoroutineContext().ensureActive()
            return Result.failure(e)
        }
        handled
            .onSuccess { markProcessed(event) }
            .onFailure { release(event, deliveryId) }
        return handled.map { DeliveryOutcome.HANDLED }
    }

    private suspend fun markProcessed(event: Event<String>) {
        runSuspendCatching { eventIdempotencyStorage.markProcessed(consumerGroup, event.topic, event.idempotencyKey) }
            .onFailure { logger.error("Failed to mark event as processed, topic: {}, idempotencyKey: {}. Error: {}", event.topic, event.idempotencyKey, it.message) }
    }

    private suspend fun release(event: Event<String>, deliveryId: String) = withContext(NonCancellable) {
        runSuspendCatching { eventIdempotencyStorage.release(consumerGroup, event.topic, event.idempotencyKey, deliveryId) }
            .onFailure { logger.error("Failed to release event claim, topic: {}, idempotencyKey: {}. Error: {}", event.topic, event.idempotencyKey, it.message) }
    }

    private companion object {
        val CLAIM_POLL_INTERVAL = 1.seconds
    }
}
