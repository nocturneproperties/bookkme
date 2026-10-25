package com.bookk.core.data.eventstreaming.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.data.orm.table.ExhaustedEventTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class ExhaustedEventEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var originalTopic by ExhaustedEventTable.originalTopic
    var idempotencyKey by ExhaustedEventTable.idempotencyKey
    var attempt by ExhaustedEventTable.attempt
    var payload by ExhaustedEventTable.payload

    fun domain(): DltEvent = DltEvent(
        payload = payload.bytes,
        originalTopic = originalTopic,
        attempt = attempt,
        topic = originalTopic,
        idempotencyKey = idempotencyKey
    )

    companion object : DecoratorUuidEntityClass<ExhaustedEventEntity>(ExhaustedEventTable)
}
