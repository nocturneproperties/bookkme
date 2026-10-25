package com.bookk.core.data.repository

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.deleteIfEquals
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.core.data.cache.setIfAbsent
import com.bookk.core.data.eventstreaming.EventClaim
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
private class EventProcessingRecord(
    @ProtoNumber(1) val processed: Boolean,
    @ProtoNumber(2) val deliveryId: String = ""
)

class CacheEventIdempotencyStorage(
    private val cacheClient: CacheClient<String>
) : EventIdempotencyStorage {

    override val claimTtl: Duration = CLAIM_TTL

    override suspend fun claim(consumerGroup: String, topic: String, idempotencyKey: String, deliveryId: String): EventClaim {
        val key = key(consumerGroup, topic, idempotencyKey)
        repeat(CLAIM_ATTEMPTS) {
            if (cacheClient.setIfAbsent(key, claimOf(deliveryId), CLAIM_TTL)) return EventClaim.CLAIMED
            val record: EventProcessingRecord = cacheClient.get(key) ?: return@repeat
            return if (record.processed) EventClaim.ALREADY_PROCESSED else EventClaim.IN_PROGRESS
        }
        return EventClaim.IN_PROGRESS
    }

    override suspend fun markProcessed(consumerGroup: String, topic: String, idempotencyKey: String) {
        cacheClient.set(key(consumerGroup, topic, idempotencyKey), EventProcessingRecord(processed = true), PROCESSED_TTL)
    }

    override suspend fun release(consumerGroup: String, topic: String, idempotencyKey: String, deliveryId: String) {
        cacheClient.deleteIfEquals(key(consumerGroup, topic, idempotencyKey), claimOf(deliveryId))
    }

    private fun claimOf(deliveryId: String) = EventProcessingRecord(processed = false, deliveryId = deliveryId)

    private fun key(consumerGroup: String, topic: String, idempotencyKey: String) =
        "event:$consumerGroup:$topic:$idempotencyKey"

    private companion object {
        const val CLAIM_ATTEMPTS = 3
        val CLAIM_TTL = 1.minutes
        val PROCESSED_TTL = 1.hours
    }
}
