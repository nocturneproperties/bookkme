package com.bookk.core.data.eventstreaming

import com.bookk.core.data.eventstreaming.EventStreaming.Consumer
import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.data.eventstreaming.EventStreaming.Producer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface EventStreaming {
    interface Consumer<K> {
        fun <T : Event<K>> registerReceiver(
            topic: K,
            type: KType,
            onEvent: suspend (T) -> Unit
        ): Consumer<K>

        fun start(scope: CoroutineScope): Job
    }

    interface Producer<K> {
        suspend fun <T : Event<K>> send(data: T, kType: KType)
    }

    interface RawProducer<K> {
        suspend fun publish(topic: K, partitionKey: String?, payload: ByteArray)

        suspend fun publishAll(records: List<RawRecord<K>>) {
            records.forEach { publish(it.topic, it.partitionKey, it.payload) }
        }
    }

    class RawRecord<K>(
        val topic: K,
        val partitionKey: String?,
        val payload: ByteArray
    )

    interface Event<K> {
        val topic: K
        val idempotencyKey: String
        val partitionKey: String? get() = null

        override fun equals(other: Any?): Boolean
        override fun hashCode(): Int
    }
}

suspend inline fun <reified T : Event<K>, K> Producer<K>.send(data: T) {
    send(data, typeOf<T>())
}

inline fun <reified T : Any, K> Consumer<K>.registerReceiver(
    topic: K,
    noinline onEvent: suspend (T) -> Unit
): Consumer<K> {
    return registerReceiver(topic, typeOf<T>(), onEvent)
}

inline fun <reified T : Any, K, R> Consumer<K>.registerResultReceiver(
    topic: K,
    noinline onEvent: suspend (T) -> Result<R>
): Consumer<K> {
    return registerReceiver(topic, typeOf<T>()) { it: T -> onEvent(it).getOrThrow() }
}

typealias StandardEventProducer = Producer<String>

typealias StandardEventConsumer = Consumer<String>