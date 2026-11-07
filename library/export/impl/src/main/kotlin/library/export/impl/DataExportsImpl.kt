package library.export.impl

import library.export.DataExport
import library.export.DataExportDocument
import library.export.DataExportStatus
import library.export.DataExports
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DataExportsImpl(
    private val dataSource: DataExportDataSource,
    private val clock: Clock,
) : DataExports {

    override suspend fun start(ownerId: Uuid): DataExport {
        val now = clock.now()
        val current = dataSource.getForUpdate(ownerId)
        when {
            current == null -> dataSource.insert(ownerId, now)
            current.isQueued -> return current.view(now)
            current.state == DataExportState.READY && now < current.availableAgainAt -> throw DataExports.Error.TooFrequent()
            else -> dataSource.restart(ownerId, now)
        }
        return DataExport(DataExportStatus.RUNNING, requestedAt = now, expiresAt = null, availableAgainAt = now + DataExports.MIN_INTERVAL)
    }

    override suspend fun status(ownerId: Uuid): DataExport = dataSource.get(ownerId)?.view(clock.now()) ?: DataExport.NONE

    override suspend fun document(ownerId: Uuid): DataExportDocument? = dataSource.document(ownerId, clock.now())?.let(::DataExportDocument)

    override suspend fun discard(ownerId: Uuid) = dataSource.delete(ownerId)

    private val DataExportRecord.isQueued: Boolean
        get() = state == DataExportState.PENDING || state == DataExportState.RUNNING

    private val DataExportRecord.availableAgainAt: Instant
        get() = requestedAt + DataExports.MIN_INTERVAL

    private fun DataExportRecord.view(now: Instant): DataExport = when {
        isQueued -> DataExport(DataExportStatus.RUNNING, requestedAt, expiresAt = null, availableAgainAt = availableAgainAt)
        state == DataExportState.FAILED -> DataExport(DataExportStatus.FAILED, requestedAt, expiresAt = null, availableAgainAt = null)
        expiresAt == null || expiresAt <= now -> DataExport.NONE
        else -> DataExport(DataExportStatus.READY, requestedAt, expiresAt, availableAgainAt = availableAgainAt.takeIf { it > now })
    }
}
