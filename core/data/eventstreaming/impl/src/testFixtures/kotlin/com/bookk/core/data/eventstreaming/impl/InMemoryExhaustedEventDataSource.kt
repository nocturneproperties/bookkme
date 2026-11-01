package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class InMemoryExhaustedEventDataSource(
    private val transactionManager: TransactionManager,
) : ExhaustedEventDataSource {
    private val events = ConcurrentLinkedQueue<DltEvent>()

    override suspend fun save(dltEvent: DltEvent) {
        check(transactionManager.isInTransaction()) { "No transaction in context." }
        if (events.none { it.idempotencyKey == dltEvent.idempotencyKey }) {
            events += dltEvent
        }
    }

    override suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent> =
        events.filter { it.originalTopic == originalTopic }
}

class FlakyExhaustedEventDataSource(
    private val failuresBeforeSuccess: Int,
    transactionManager: TransactionManager,
) : ExhaustedEventDataSource {
    private val delegate = InMemoryExhaustedEventDataSource(transactionManager)
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
