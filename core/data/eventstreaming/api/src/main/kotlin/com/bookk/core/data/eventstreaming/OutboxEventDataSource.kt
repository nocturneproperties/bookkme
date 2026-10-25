package com.bookk.core.data.eventstreaming

interface OutboxEventDataSource {
    suspend fun enqueue(topic: String, partitionKey: String?, payload: ByteArray)
    suspend fun lockPending(limit: Int): List<OutboxEvent>
    suspend fun delete(ids: List<Long>)
}
