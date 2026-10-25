package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class InMemoryExhaustedEventDataSource : ExhaustedEventDataSource {
    private val events = ConcurrentLinkedQueue<DltEvent>()

    override suspend fun save(dltEvent: DltEvent) {
        if (events.none { it.idempotencyKey == dltEvent.idempotencyKey }) {
            events += dltEvent
        }
    }

    override suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent> =
        events.filter { it.originalTopic == originalTopic }
}

class FlakyExhaustedEventDataSource(
    private val failuresBeforeSuccess: Int,
    private val delegate: ExhaustedEventDataSource = InMemoryExhaustedEventDataSource(),
) : ExhaustedEventDataSource {
    private val attempts = AtomicInteger(0)

    override suspend fun save(dltEvent: DltEvent) {
        if (attempts.getAndIncrement() < failuresBeforeSuccess) {
            throw RuntimeException("simulated db failure")
        }
        delegate.save(dltEvent)
    }

    override suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent> =
        delegate.findByOriginalTopic(originalTopic)
}
