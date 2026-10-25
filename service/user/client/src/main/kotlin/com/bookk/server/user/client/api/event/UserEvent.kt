package com.bookk.server.user.client.api.event

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed interface UserEvent : EventStreaming.Event<String> {
    @Serializable
    data class Updated(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val lastName: String,
        @ProtoNumber(4) val email: String,
        @ProtoNumber(5) val phone: String?,
        @ProtoNumber(6) val updatedAt: Instant,
        @ProtoNumber(7) override val idempotencyKey: String = Uuid.random().toString()
    ) : UserEvent {
        @ProtoNumber(8)
        override val topic: String = TOPIC
        override val partitionKey: String get() = userId.toString()

        companion object {
            const val TOPIC = "user.updated"
        }
    }
}