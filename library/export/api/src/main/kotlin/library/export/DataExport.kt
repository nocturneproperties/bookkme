package library.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class DataExport(
    @ProtoNumber(1) val status: DataExportStatus,
    @ProtoNumber(2) val requestedAt: Instant?,
    @ProtoNumber(3) val expiresAt: Instant?,
    @ProtoNumber(4) val availableAgainAt: Instant?,
) {
    companion object {
        val NONE =
            DataExport(status = DataExportStatus.NONE, requestedAt = null, expiresAt = null, availableAgainAt = null)

        fun stub(
            status: DataExportStatus = DataExportStatus.RUNNING,
            requestedAt: Instant? = Instant.fromEpochMilliseconds(0),
            expiresAt: Instant? = null,
            availableAgainAt: Instant? = null,
        ) = DataExport(status, requestedAt, expiresAt, availableAgainAt)
    }
}
