package com.bookk.core.data.eventstreaming

import kotlin.time.Duration

interface EventIdempotencyStorage {
    val claimTtl: Duration

    suspend fun claim(consumerGroup: String, topic: String, idempotencyKey: String, deliveryId: String): EventClaim

    suspend fun markProcessed(consumerGroup: String, topic: String, idempotencyKey: String)

    suspend fun release(consumerGroup: String, topic: String, idempotencyKey: String, deliveryId: String)
}

enum class EventClaim {
    CLAIMED,
    IN_PROGRESS,
    ALREADY_PROCESSED
}
