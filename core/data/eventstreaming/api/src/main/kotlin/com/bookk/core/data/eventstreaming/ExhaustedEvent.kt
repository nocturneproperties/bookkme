package com.bookk.core.data.eventstreaming

import kotlin.uuid.Uuid

data class ExhaustedEvent(
    val id: Uuid,
    val event: DltEvent
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            originalTopic: String = "topic",
            payload: ByteArray = byteArrayOf(),
            attempt: Int = DltRetry.DEFAULT_MAX_ATTEMPTS
        ) = ExhaustedEvent(
            id = id,
            event = DltEvent(payload = payload, originalTopic = originalTopic, attempt = attempt, topic = originalTopic)
        )
    }
}
