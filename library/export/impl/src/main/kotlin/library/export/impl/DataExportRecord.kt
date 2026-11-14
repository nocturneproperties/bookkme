package library.export.impl

import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class DataExportRecord(
    val ownerId: Uuid,
    val state: DataExportState,
    val requestedAt: Instant,
    val attempts: Int,
    val expiresAt: Instant?,
    val keyId: Uuid? = null,
) {
    companion object {
        fun stub(
            ownerId: Uuid = Uuid.random(),
            state: DataExportState = DataExportState.PENDING,
            requestedAt: Instant = Instant.fromEpochMilliseconds(0),
            attempts: Int = 0,
            expiresAt: Instant? = null,
            keyId: Uuid? = null,
        ): DataExportRecord {
            return DataExportRecord(ownerId, state, requestedAt, attempts, expiresAt, keyId)
        }
    }
}
