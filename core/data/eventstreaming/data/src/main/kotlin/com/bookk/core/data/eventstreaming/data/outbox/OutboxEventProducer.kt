package com.bookk.core.data.eventstreaming.data.outbox

import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.OutboxEventDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import kotlin.reflect.KType

class OutboxEventProducer(
    private val outboxEventDataSource: OutboxEventDataSource,
    private val transactionManager: TransactionManager,
    private val protoBuf: ProtoBuf
) : EventStreaming.Producer<String> {

    override suspend fun <T : EventStreaming.Event<String>> send(data: T, kType: KType) {
        check(transactionManager.isInTransaction()) {
            "Event ${data.topic} must be sent inside a transaction so it commits or rolls back with it"
        }
        val payload = protoBuf.encodeToByteArray(protoBuf.serializersModule.serializer(kType), data)
        outboxEventDataSource.enqueue(topic = data.topic, partitionKey = data.partitionKey, payload = payload)
    }
}
