package com.bookk.core.data.eventstreaming

import kotlin.uuid.Uuid

interface ExhaustedEventDataSource {
    suspend fun save(dltEvent: DltEvent)
    suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent>
    suspend fun requestReplayOfAll()
    suspend fun lockReplayRequested(limit: Int): List<ExhaustedEvent>
    suspend fun delete(ids: List<Uuid>)
    suspend fun clearReplayRequest(ids: List<Uuid>)
    suspend fun countByOriginalTopic(): Map<String, Long>
}
