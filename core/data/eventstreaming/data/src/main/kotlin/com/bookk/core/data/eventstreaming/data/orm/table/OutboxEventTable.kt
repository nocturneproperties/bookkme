package com.bookk.core.data.eventstreaming.data.orm.table

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object OutboxEventTable : LongIdTable("outbox_event") {
    val topic = varchar("topic", 255)
    val partitionKey = varchar("partition_key", 255).nullable()
    val payload = blob("payload")
    val createdAt = timestamp("createdAt").clientDefault { Clock.System.now() }
}
