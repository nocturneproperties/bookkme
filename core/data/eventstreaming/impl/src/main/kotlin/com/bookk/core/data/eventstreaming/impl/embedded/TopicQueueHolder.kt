package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.EventStreaming.Event
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.CopyOnWriteArrayList

class TopicQueueHolder<Key : Any> {

    private val subscribersByTopic = ConcurrentMap<Key, CopyOnWriteArrayList<Channel<Event<Key>>>>()

    fun subscribe(topic: Key): ReceiveChannel<Event<Key>> {
        val channel = Channel<Event<Key>>(Channel.UNLIMITED)
        subscribersByTopic.getOrPut(topic) { CopyOnWriteArrayList() }.add(channel)
        return channel
    }

    suspend fun publish(event: Event<Key>) {
        subscribersByTopic[event.topic]?.forEach { it.send(event) }
    }
}
