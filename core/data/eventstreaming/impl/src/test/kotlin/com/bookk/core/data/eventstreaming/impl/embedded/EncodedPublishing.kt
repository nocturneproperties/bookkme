package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

internal val encodedEventProtoBuf = ProtoBuf { encodeDefaults = true }

internal suspend inline fun <reified T : EventStreaming.Event<String>> EventStreaming.RawProducer<String>.publishEncoded(event: T) {
    publish(
        topic = event.topic,
        partitionKey = event.partitionKey,
        payload = encodedEventProtoBuf.encodeToByteArray(event)
    )
}
