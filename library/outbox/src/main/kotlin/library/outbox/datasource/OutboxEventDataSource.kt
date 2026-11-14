package library.outbox.datasource

interface OutboxEventDataSource {
    suspend fun enqueue(topic: String, partitionKey: String?, payload: ByteArray, personalDataEncrypted: Boolean)
    suspend fun lockPending(limit: Int): List<OutboxEvent>
    suspend fun delete(ids: List<Long>)
}
