package library.export.impl.orm

import library.export.impl.DataExportState
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object DataExportTable : Table("data_export") {
    val ownerId = uuid("owner_id")
    val state = enumerationByName<DataExportState>("state", 16).index()
    val requestedAt = timestamp("requested_at")
    val attempts = integer("attempts").default(0)
    val claimId = uuid("claim_id").nullable()
    val claimedUntil = timestamp("claimed_until").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val expiresAt = timestamp("expires_at").nullable().index()
    val document = blob("document").nullable()
    val keyId = uuid("key_id").nullable()

    override val primaryKey = PrimaryKey(ownerId)
}
