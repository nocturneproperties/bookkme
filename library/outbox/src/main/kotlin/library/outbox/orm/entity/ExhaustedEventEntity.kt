package library.outbox.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.core.data.eventstreaming.DltEvent
import library.outbox.datasource.ExhaustedEventRow
import library.outbox.orm.table.ExhaustedEventTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class ExhaustedEventEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var originalTopic by ExhaustedEventTable.originalTopic
    var idempotencyKey by ExhaustedEventTable.idempotencyKey
    var attempt by ExhaustedEventTable.attempt
    var payload by ExhaustedEventTable.payload
    var personalDataEncrypted by ExhaustedEventTable.personalDataEncrypted

    fun domain(): DltEvent = DltEvent(
        payload = payload.bytes,
        originalTopic = originalTopic,
        attempt = attempt,
        topic = originalTopic,
        idempotencyKey = idempotencyKey,
    )

    fun row(): ExhaustedEventRow {
        return ExhaustedEventRow(id = id.value, event = domain(), personalDataEncrypted = personalDataEncrypted)
    }

    companion object : DecoratorUuidEntityClass<ExhaustedEventEntity>(ExhaustedEventTable)
}
