package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.EventStreaming
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import kotlin.reflect.KType

class EmbeddedEventProducer(
    private val topicQueueHolder: TopicQueueHolder<String>,
    private val protoBuf: ProtoBuf,
) : EventStreaming.RawProducer<String> {

    override suspend fun publish(topic: String, partitionKey: String?, payload: ByteArray) {
        topicQueueHolder.publish(EmbeddedEncodedEvent(topic = topic, partitionKey = partitionKey, payload = payload))
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun replay(dltEvent: DltEvent, kType: KType) {
        val event = protoBuf.decodeFromByteArray(protoBuf.serializersModule.serializer(kType), dltEvent.payload) as EventStreaming.Event<String>
        topicQueueHolder.publish(event)
    }
}
