package library.outbox.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import library.outbox.orm.entity.ExhaustedEventEntity
import library.outbox.orm.table.ExhaustedEventTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ExhaustedEventRowDataSourceImpl : DataSource(), ExhaustedEventRowDataSource {

    override suspend fun save(dltEvent: DltEvent, personalDataEncrypted: Boolean) {
        dbQuery {
            runCatching {
                ExhaustedEventTable.insertAndGetId {
                    it[originalTopic] = dltEvent.originalTopic
                    it[idempotencyKey] = dltEvent.idempotencyKey
                    it[attempt] = dltEvent.attempt
                    it[payload] = ExposedBlob(dltEvent.payload)
                    it[ExhaustedEventTable.personalDataEncrypted] = personalDataEncrypted
                }
            }.onFailure { failure -> if (failure.toDomain() !is Error.UniqueConstraintFailed) throw failure }
        }
    }

    override suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent> = dbQuery {
        ExhaustedEventEntity.find { ExhaustedEventTable.originalTopic eq originalTopic }
            .map { it.domain() }
    }

    override suspend fun requestReplayOfAll() {
        dbQuery { ExhaustedEventTable.update { it[replayRequested] = true } }
    }

    override suspend fun lockReplayRequested(limit: Int): List<ExhaustedEventRow> = dbQuery {
        ExhaustedEventEntity.find { ExhaustedEventTable.replayRequested eq true }
            .orderBy(ExhaustedEventTable.createdAt to SortOrder.ASC, ExhaustedEventTable.id to SortOrder.ASC)
            .limit(limit)
            .forUpdate()
            .map { it.row() }
    }

    override suspend fun delete(ids: List<Uuid>) {
        if (ids.isEmpty()) return
        dbQuery { ExhaustedEventTable.deleteWhere { ExhaustedEventTable.id inList ids } }
    }

    override suspend fun clearReplayRequest(ids: List<Uuid>) {
        if (ids.isEmpty()) return
        dbQuery {
            ExhaustedEventTable.update({ ExhaustedEventTable.id inList ids }) {
                it[replayRequested] = false
            }
        }
    }

    override suspend fun countByOriginalTopic(): Map<String, Long> = dbQuery {
        val parked = ExhaustedEventTable.id.count()
        ExhaustedEventTable.select(ExhaustedEventTable.originalTopic, parked)
            .groupBy(ExhaustedEventTable.originalTopic)
            .associate { it[ExhaustedEventTable.originalTopic] to it[parked] }
    }

    override suspend fun deleteParkedBefore(cutoff: Instant): Int = dbQuery {
        ExhaustedEventTable.deleteWhere { ExhaustedEventTable.createdAt less cutoff }
    }
}
