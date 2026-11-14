package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.ParkedPayload
import io.ktor.util.logging.Logger

internal suspend fun ExhaustedEvent.replayReadable(logger: Logger, publish: suspend (ByteArray) -> Boolean): Boolean {
    return when (val parked = payload) {
        is ParkedPayload.Readable -> publish(parked.bytes)

        ParkedPayload.SubjectErased -> {
            logger.info("Dropped a parked event of an erased data subject, topic: {}", originalTopic)
            true
        }

        is ParkedPayload.Unreadable -> {
            logger.error("Cannot replay an unreadable event, topic: {}. Error: {}", originalTopic, parked.reason)
            false
        }
    }
}
