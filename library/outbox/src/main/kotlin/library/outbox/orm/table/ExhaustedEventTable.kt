package library.outbox.orm.table

import com.bookk.core.data.database.BaseUUIDTable

object ExhaustedEventTable : BaseUUIDTable("exhausted_event") {
    val originalTopic = varchar("original_topic", 255).index()
    val idempotencyKey = varchar("idempotency_key", 255).uniqueIndex()
    val attempt = integer("attempt")
    val payload = blob("payload")
    val replayRequested = bool("replay_requested").default(false)
    val personalDataEncrypted = bool("personal_data_encrypted").default(false)
}
