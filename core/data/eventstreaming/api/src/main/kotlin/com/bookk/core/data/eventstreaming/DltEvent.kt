package com.bookk.core.data.eventstreaming

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class DltEvent(
    @ProtoNumber(1) val payload: ByteArray,
    @ProtoNumber(2) val originalTopic: String,
    @ProtoNumber(3) val attempt: Int = 0,
    @ProtoNumber(4) override val topic: String,
    @ProtoNumber(5) override val idempotencyKey: String = Uuid.random().toString()
) : EventStreaming.Event<String> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DltEvent) return false
        return payload.contentEquals(other.payload) &&
            originalTopic == other.originalTopic &&
            attempt == other.attempt &&
            topic == other.topic &&
            idempotencyKey == other.idempotencyKey
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + originalTopic.hashCode()
        result = 31 * result + attempt
        result = 31 * result + topic.hashCode()
        result = 31 * result + idempotencyKey.hashCode()
        return result
    }
}
