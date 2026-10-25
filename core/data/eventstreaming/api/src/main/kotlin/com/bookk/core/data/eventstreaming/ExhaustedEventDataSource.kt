package com.bookk.core.data.eventstreaming

interface ExhaustedEventDataSource {
    suspend fun save(dltEvent: DltEvent)
    suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent>
}
