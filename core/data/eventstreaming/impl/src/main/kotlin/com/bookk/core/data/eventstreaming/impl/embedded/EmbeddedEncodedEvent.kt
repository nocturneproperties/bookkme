package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlin.uuid.Uuid

class EmbeddedEncodedEvent(
    override val topic: String,
    override val partitionKey: String?,
    val payload: ByteArray,
    override val idempotencyKey: String = Uuid.random().toString()
) : EventStreaming.Event<String> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedEncodedEvent) return false
        return topic == other.topic &&
            partitionKey == other.partitionKey &&
            payload.contentEquals(other.payload) &&
            idempotencyKey == other.idempotencyKey
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + (partitionKey?.hashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + idempotencyKey.hashCode()
        return result
    }
}
