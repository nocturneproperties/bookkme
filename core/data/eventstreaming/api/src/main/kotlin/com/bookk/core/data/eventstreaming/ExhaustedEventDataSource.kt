package com.bookk.core.data.eventstreaming

import com.bookk.core.data.eventstreaming.EventStreaming.Event
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ExhaustedEventDataSource {
    suspend fun save(event: Event<String>, dltEvent: DltEvent): Parked
    suspend fun requestReplayOfAll()
    suspend fun lockReplayRequested(limit: Int): List<ExhaustedEvent>
    suspend fun delete(ids: List<Uuid>)
    suspend fun clearReplayRequest(ids: List<Uuid>)
    suspend fun countByOriginalTopic(): Map<String, Long>
    suspend fun deleteParkedBefore(cutoff: Instant): Int
}

sealed interface Parked {
    data object Stored : Parked

    data object SubjectErased : Parked
}
