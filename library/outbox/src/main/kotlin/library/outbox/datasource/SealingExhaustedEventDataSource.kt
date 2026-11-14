package library.outbox.datasource

import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.data.eventstreaming.ExhaustedEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.Parked
import com.bookk.core.data.eventstreaming.ParkedPayload
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.StoredPayload
import library.outbox.personal.UnreadableSealedPayload
import library.outbox.personal.readablePayload
import library.outbox.personal.storedPayload
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SealingExhaustedEventDataSource(
    private val rows: ExhaustedEventRowDataSource,
    private val personalDataCipher: PersonalDataCipher,
) : ExhaustedEventDataSource {

    override suspend fun save(event: Event<String>, dltEvent: DltEvent): Parked {
        val stored = personalDataCipher.storedPayload(event, dltEvent.payload) ?: return Parked.SubjectErased
        rows.save(dltEvent.copy(payload = stored.payload), stored.personalDataEncrypted)
        return Parked.Stored
    }

    override suspend fun lockReplayRequested(limit: Int): List<ExhaustedEvent> {
        return rows.lockReplayRequested(limit).map { row ->
            ExhaustedEvent(id = row.id, originalTopic = row.event.originalTopic, payload = parkedPayloadOf(row))
        }
    }

    override suspend fun requestReplayOfAll() {
        rows.requestReplayOfAll()
    }

    override suspend fun delete(ids: List<Uuid>) {
        rows.delete(ids)
    }

    override suspend fun clearReplayRequest(ids: List<Uuid>) {
        rows.clearReplayRequest(ids)
    }

    override suspend fun countByOriginalTopic(): Map<String, Long> {
        return rows.countByOriginalTopic()
    }

    override suspend fun deleteParkedBefore(cutoff: Instant): Int {
        return rows.deleteParkedBefore(cutoff)
    }

    private suspend fun parkedPayloadOf(row: ExhaustedEventRow): ParkedPayload {
        val stored = StoredPayload(row.event.payload, row.personalDataEncrypted)
        val readable = try {
            personalDataCipher.readablePayload(row.event.originalTopic, stored)
        } catch (unreadable: UnreadableSealedPayload) {
            return ParkedPayload.Unreadable(unreadable.message.orEmpty())
        }
        if (readable == null) return ParkedPayload.SubjectErased
        return ParkedPayload.Readable(readable)
    }
}
