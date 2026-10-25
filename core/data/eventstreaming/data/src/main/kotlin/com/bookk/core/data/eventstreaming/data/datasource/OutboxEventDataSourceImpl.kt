package com.bookk.core.data.eventstreaming.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.data.eventstreaming.OutboxEvent
import com.bookk.core.data.eventstreaming.OutboxEventDataSource
import com.bookk.core.data.eventstreaming.data.orm.table.OutboxEventTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class OutboxEventDataSourceImpl : DataSource(), OutboxEventDataSource {

    override suspend fun enqueue(topic: String, partitionKey: String?, payload: ByteArray) {
        dbQuery {
            OutboxEventTable.insert {
                it[OutboxEventTable.topic] = topic
                it[OutboxEventTable.partitionKey] = partitionKey
                it[OutboxEventTable.payload] = ExposedBlob(payload)
            }
        }
    }

    override suspend fun lockPending(limit: Int): List<OutboxEvent> = dbQuery {
        OutboxEventTable.selectAll()
            .orderBy(OutboxEventTable.id, SortOrder.ASC)
            .limit(limit)
            .forUpdate()
            .map { it.toOutboxEvent() }
    }

    override suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        dbQuery { OutboxEventTable.deleteWhere { OutboxEventTable.id inList ids } }
    }

    private fun ResultRow.toOutboxEvent() = OutboxEvent(
        id = this[OutboxEventTable.id].value,
        topic = this[OutboxEventTable.topic],
        partitionKey = this[OutboxEventTable.partitionKey],
        payload = this[OutboxEventTable.payload].bytes
    )
}
