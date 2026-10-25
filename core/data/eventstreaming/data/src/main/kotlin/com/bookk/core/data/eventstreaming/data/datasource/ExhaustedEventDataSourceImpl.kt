package com.bookk.core.data.eventstreaming.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.data.eventstreaming.data.orm.entity.ExhaustedEventEntity
import com.bookk.core.data.eventstreaming.data.orm.table.ExhaustedEventTable
import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.insertAndGetId

class ExhaustedEventDataSourceImpl : DataSource(), ExhaustedEventDataSource {

    override suspend fun save(dltEvent: DltEvent) {
        dbQuery {
            runCatching {
                ExhaustedEventTable.insertAndGetId {
                    it[originalTopic] = dltEvent.originalTopic
                    it[idempotencyKey] = dltEvent.idempotencyKey
                    it[attempt] = dltEvent.attempt
                    it[payload] = ExposedBlob(dltEvent.payload)
                }
            }.onFailure { failure -> if (failure.toDomain() !is Error.UniqueConstraintFailed) throw failure }
        }
    }

    override suspend fun findByOriginalTopic(originalTopic: String): List<DltEvent> = dbQuery {
        ExhaustedEventEntity.find { ExhaustedEventTable.originalTopic eq originalTopic }
            .map { it.domain() }
    }
}
