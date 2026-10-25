package com.bookk.core.data.eventstreaming.impl.kafka

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
internal data class KeyedTestEvent(
    val entityId: String,
    val sequence: Int,
    override val topic: String,
    override val idempotencyKey: String = Uuid.random().toString()
) : EventStreaming.Event<String> {
    override val partitionKey: String get() = entityId
}

@Serializable
internal data class NullableFieldTestEvent(
    val required: String,
    val optional: String?,
    override val topic: String,
    override val idempotencyKey: String = Uuid.random().toString()
) : EventStreaming.Event<String> {
    override val partitionKey: String get() = required
}

@Serializable
internal data class UnkeyedTestEvent(
    val payload: String,
    override val topic: String,
    override val idempotencyKey: String = Uuid.random().toString()
) : EventStreaming.Event<String>
