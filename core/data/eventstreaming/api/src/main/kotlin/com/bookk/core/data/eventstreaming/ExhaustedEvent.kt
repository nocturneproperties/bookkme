package com.bookk.core.data.eventstreaming

import kotlin.uuid.Uuid

data class ExhaustedEvent(
    val id: Uuid,
    val originalTopic: String,
    val payload: ParkedPayload,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            originalTopic: String = "topic",
            payload: ParkedPayload = ParkedPayload.Readable(byteArrayOf()),
        ): ExhaustedEvent {
            return ExhaustedEvent(id = id, originalTopic = originalTopic, payload = payload)
        }
    }
}

sealed interface ParkedPayload {
    class Readable(val bytes: ByteArray) : ParkedPayload {
        override fun equals(other: Any?): Boolean {
            return other is Readable && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }

    data object SubjectErased : ParkedPayload

    data class Unreadable(val reason: String) : ParkedPayload
}
