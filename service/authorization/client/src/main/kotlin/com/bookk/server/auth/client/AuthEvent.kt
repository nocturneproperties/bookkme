package com.bookk.server.auth.client

import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.domain.entity.Language
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AuthEvent : EventStreaming.Event<String> {
    @Serializable
    data class AccountDormancyWarning(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val deletesAt: Instant,
        @ProtoNumber(3) val final: Boolean,
        @ProtoNumber(4) override val idempotencyKey: String = Uuid.random().toString(),
    ) : AuthEvent {
        @ProtoNumber(5)
        override val topic: String = TOPIC
        override val partitionKey: String get() = userId.toString()

        companion object {
            const val TOPIC = "auth.account_dormancy_warning"
        }
    }

    @Serializable
    data class UserDeleted(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) override val idempotencyKey: String = Uuid.random().toString(),
    ) : AuthEvent {
        @ProtoNumber(3)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "auth.user_deleted"
        }
    }

    @Serializable
    data class DeviceCreated(
        @ProtoNumber(1) val authId: Uuid,
        @ProtoNumber(2) val userId: Uuid,
        @ProtoNumber(3) val deviceUuid: Uuid,
        @ProtoNumber(4) val language: Language,
        @ProtoNumber(5) override val idempotencyKey: String = Uuid.random().toString(),
    ) : AuthEvent {
        @ProtoNumber(6)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "auth.device_created"
        }
    }

    @Serializable
    data class DeviceLanguageUpdated(
        @ProtoNumber(1) val deviceUuid: Uuid,
        @ProtoNumber(2) val language: Language,
        @ProtoNumber(5) val authId: Uuid = Uuid.NIL,
        @ProtoNumber(3) override val idempotencyKey: String = Uuid.random().toString(),
    ) : AuthEvent {
        @ProtoNumber(4)
        override val topic: String = TOPIC
        override val partitionKey: String get() = deviceUuid.toString()

        companion object {
            const val TOPIC = "auth.device_language_updated"
        }
    }

    @Serializable
    data class DeviceDeleted(
        @ProtoNumber(1) val deviceUuid: Uuid,
        @ProtoNumber(4) val authId: Uuid = Uuid.NIL,
        @ProtoNumber(2) override val idempotencyKey: String = Uuid.random().toString(),
    ) : AuthEvent {
        @ProtoNumber(3)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "auth.device_deleted"
        }
    }
}
