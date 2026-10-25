package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.EventStreaming

class EmbeddedEventProducer(
    private val topicQueueHolder: TopicQueueHolder<String>,
) : EventStreaming.RawProducer<String> {

    override suspend fun publish(topic: String, partitionKey: String?, payload: ByteArray) {
        topicQueueHolder.publish(EmbeddedEncodedEvent(topic = topic, partitionKey = partitionKey, payload = payload))
    }
}
