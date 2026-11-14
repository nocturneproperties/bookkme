package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.Parked
import com.bookk.core.data.eventstreaming.ParkedPayload
import com.bookk.core.data.eventstreaming.PersonalEvent
import com.bookk.core.domain.datasource.transaction.TransactionManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

open class InMemoryExhaustedEventDataSource(
    private val transactionManager: TransactionManager,
) : ExhaustedEventDataSource {
    private class Row(
        val id: Uuid,
        val dltEvent: DltEvent,
        val payload: ParkedPayload,
        @Volatile var replayRequested: Boolean = false,
        @Volatile var parkedAt: Instant = Clock.System.now(),
    ) {
        fun exhausted(): ExhaustedEvent {
            return ExhaustedEvent(id = id, originalTopic = dltEvent.originalTopic, payload = payload)
        }
    }

    private val rows = ConcurrentLinkedQueue<Row>()
    private val erased = ConcurrentHashMap.newKeySet<DataSubject>()

    fun erase(subject: DataSubject) {
        erased += subject
    }

    override suspend fun save(event: Event<String>, dltEvent: DltEvent): Parked {
        requireTransaction()
        if (event is PersonalEvent && event.dataSubject in erased) return Parked.SubjectErased
        if (rows.none { it.dltEvent.idempotencyKey == dltEvent.idempotencyKey }) {
            rows += Row(id = Uuid.random(), dltEvent = dltEvent, payload = ParkedPayload.Readable(dltEvent.payload))
        }
        return Parked.Stored
    }

    override suspend fun requestReplayOfAll() {
        requireTransaction()
        rows.forEach { it.replayRequested = true }
    }

    override suspend fun lockReplayRequested(limit: Int): List<ExhaustedEvent> {
        requireTransaction()
        return rows.filter { it.replayRequested }.take(limit).map { it.exhausted() }
    }

    override suspend fun delete(ids: List<Uuid>) {
        requireTransaction()
        rows.removeIf { it.id in ids }
    }

    override suspend fun clearReplayRequest(ids: List<Uuid>) {
        requireTransaction()
        rows.filter { it.id in ids }.forEach { it.replayRequested = false }
    }

    override suspend fun countByOriginalTopic(): Map<String, Long> {
        requireTransaction()
        return rows.groupingBy { it.dltEvent.originalTopic }.eachCount().mapValues { it.value.toLong() }
    }

    override suspend fun deleteParkedBefore(cutoff: Instant): Int {
        requireTransaction()
        val expired = rows.filter { it.parkedAt < cutoff }
        rows.removeAll(expired.toSet())
        return expired.size
    }

    fun park(
        dltEvent: DltEvent,
        replayRequested: Boolean = false,
        parkedAt: Instant = Clock.System.now(),
        payload: ParkedPayload = ParkedPayload.Readable(dltEvent.payload),
    ): ExhaustedEvent {
        val row = Row(
            id = Uuid.random(),
            dltEvent = dltEvent,
            payload = payload,
            replayRequested = replayRequested,
            parkedAt = parkedAt,
        )
        rows += row
        return row.exhausted()
    }

    fun findByOriginalTopic(originalTopic: String): List<DltEvent> {
        return rows.map { it.dltEvent }.filter { it.originalTopic == originalTopic }
    }

    fun parked(): List<ExhaustedEvent> {
        return rows.map { it.exhausted() }
    }

    fun replayRequested(): List<ExhaustedEvent> {
        return rows.filter { it.replayRequested }.map { it.exhausted() }
    }

    private suspend fun requireTransaction() {
        check(transactionManager.isInTransaction()) { "No transaction in context." }
    }
}

class FlakyExhaustedEventDataSource(
    private val failuresBeforeSuccess: Int,
    transactionManager: TransactionManager,
) : InMemoryExhaustedEventDataSource(transactionManager) {

    private val attempts = AtomicInteger(0)

    override suspend fun save(event: Event<String>, dltEvent: DltEvent): Parked {
        if (attempts.getAndIncrement() < failuresBeforeSuccess) {
            throw RuntimeException("simulated db failure")
        }
        return super.save(event, dltEvent)
    }
}
