package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

class InMemoryExhaustedEventDataSource(
    private val transactionManager: TransactionManager,
) : ExhaustedEventDataSource {
    private class Row(val exhausted: ExhaustedEvent, @Volatile var replayRequested: Boolean = false)

    private val rows = ConcurrentLinkedQueue<Row>()

    override suspend fun save(dltEvent: DltEvent) {
        requireTransaction()
        if (rows.none { it.exhausted.event.idempotencyKey == dltEvent.idempotencyKey }) {
            rows += Row(ExhaustedEvent(id = Uuid.random(), event = dltEvent))
        }
    }

    override suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent> =
        rows.map { it.exhausted.event }.filter { it.originalTopic == originalTopic }

    override suspend fun requestReplayOfAll() {
        requireTransaction()
        rows.forEach { it.replayRequested = true }
    }

    override suspend fun lockReplayRequested(limit: Int): List<ExhaustedEvent> {
        requireTransaction()
        return rows.filter { it.replayRequested }.take(limit).map { it.exhausted }
    }

    override suspend fun delete(ids: List<Uuid>) {
        requireTransaction()
        rows.removeIf { it.exhausted.id in ids }
    }

    override suspend fun clearReplayRequest(ids: List<Uuid>) {
        requireTransaction()
        rows.filter { it.exhausted.id in ids }.forEach { it.replayRequested = false }
    }

    override suspend fun countByOriginalTopic(): Map<String, Long> {
        requireTransaction()
        return rows.groupingBy { it.exhausted.event.originalTopic }.eachCount().mapValues { it.value.toLong() }
    }

    suspend fun park(dltEvent: DltEvent, replayRequested: Boolean = false): ExhaustedEvent {
        transactionManager.transaction { save(dltEvent) }
        val row = rows.single { it.exhausted.event.idempotencyKey == dltEvent.idempotencyKey }
        row.replayRequested = replayRequested
        return row.exhausted
    }

    fun parked(): List<ExhaustedEvent> = rows.map { it.exhausted }

    fun replayRequested(): List<ExhaustedEvent> = rows.filter { it.replayRequested }.map { it.exhausted }

    private suspend fun requireTransaction() {
        check(transactionManager.isInTransaction()) { "No transaction in context." }
    }
}

class FlakyExhaustedEventDataSource private constructor(
    private val failuresBeforeSuccess: Int,
    private val delegate: InMemoryExhaustedEventDataSource,
) : ExhaustedEventDataSource by delegate {
    constructor(failuresBeforeSuccess: Int, transactionManager: TransactionManager) :
        this(failuresBeforeSuccess, InMemoryExhaustedEventDataSource(transactionManager))

    private val attempts = AtomicInteger(0)

    override suspend fun save(dltEvent: DltEvent) {
        if (attempts.getAndIncrement() < failuresBeforeSuccess) {
            throw RuntimeException("simulated db failure")
        }
        delegate.save(dltEvent)
    }
}
