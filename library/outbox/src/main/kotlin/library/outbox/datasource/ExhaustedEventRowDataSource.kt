package library.outbox.datasource

import com.bookk.core.data.eventstreaming.DltEvent
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal interface ExhaustedEventRowDataSource {
    suspend fun save(dltEvent: DltEvent, personalDataEncrypted: Boolean)
    suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent>
    suspend fun requestReplayOfAll()
    suspend fun lockReplayRequested(limit: Int): List<ExhaustedEventRow>
    suspend fun delete(ids: List<Uuid>)
    suspend fun clearReplayRequest(ids: List<Uuid>)
    suspend fun countByOriginalTopic(): Map<String, Long>
    suspend fun deleteParkedBefore(cutoff: Instant): Int
}

internal data class ExhaustedEventRow(
    val id: Uuid,
    val event: DltEvent,
    val personalDataEncrypted: Boolean,
)
