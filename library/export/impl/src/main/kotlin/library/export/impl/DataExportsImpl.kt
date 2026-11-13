package library.export.impl

import library.encryption.Decrypted
import library.encryption.SubjectKeys
import library.export.DataExport
import library.export.DataExportDocument
import library.export.DataExportStatus
import library.export.DataExports
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = LoggerFactory.getLogger(DataExportsImpl::class.java)

internal class DataExportsImpl(
    private val dataSource: DataExportDataSource,
    private val subjectKeys: SubjectKeys,
    private val clock: Clock,
) : DataExports {

    override suspend fun start(ownerId: Uuid): DataExport {
        val now = clock.now()
        val current = dataSource.getForUpdate(ownerId)
        when {
            current == null -> dataSource.insert(ownerId, now)

            current.isQueued -> return current.view(now)

            current.isReadyBefore(now) -> throw DataExports.Error.TooFrequent()

            else -> {
                dataSource.restart(ownerId, now)
                current.keyId?.let { subjectKeys.shred(it) }
            }
        }
        return DataExport(
            DataExportStatus.RUNNING,
            requestedAt = now,
            expiresAt = null,
            availableAgainAt = now + DataExports.MIN_INTERVAL,
        )
    }

    override suspend fun status(ownerId: Uuid): DataExport {
        return dataSource.get(ownerId)?.view(clock.now()) ?: DataExport.NONE
    }

    override suspend fun document(ownerId: Uuid): DataExportDocument? {
        val stored = dataSource.document(ownerId, clock.now()) ?: return null
        val keyId = stored.keyId ?: return null
        return when (val decrypted = subjectKeys.decryptor(listOf(keyId)).decrypt(keyId, stored.ciphertext)) {
            is Decrypted.Plain -> DataExportDocument(decrypted.value)

            Decrypted.Shredded -> null

            Decrypted.Missing -> {
                logger.error("The key $keyId of the data export of $ownerId is missing, so it can't be read")
                null
            }
        }
    }

    override suspend fun discard(ownerId: Uuid) {
        discardAll(listOf(ownerId))
    }

    override suspend fun discardAll(ownerIds: Collection<Uuid>) {
        if (ownerIds.isEmpty()) return
        val keyIds = dataSource.delete(ownerIds.toSet())
        if (keyIds.isNotEmpty()) subjectKeys.shredAll(keyIds)
    }

    private val DataExportRecord.isQueued: Boolean
        get() = state == DataExportState.PENDING || state == DataExportState.RUNNING

    private val DataExportRecord.availableAgainAt: Instant
        get() = requestedAt + DataExports.MIN_INTERVAL

    private fun DataExportRecord.isReadyBefore(now: Instant): Boolean {
        return state == DataExportState.READY && now < availableAgainAt
    }

    private fun DataExportRecord.view(now: Instant): DataExport = when {
        isQueued -> DataExport(
            DataExportStatus.RUNNING,
            requestedAt,
            expiresAt = null,
            availableAgainAt = availableAgainAt,
        )

        state == DataExportState.FAILED -> DataExport(
            DataExportStatus.FAILED,
            requestedAt,
            expiresAt = null,
            availableAgainAt = null,
        )

        expiresAt == null || expiresAt <= now -> DataExport.NONE

        else -> DataExport(
            DataExportStatus.READY,
            requestedAt,
            expiresAt,
            availableAgainAt = availableAgainAt.takeIf {
                it >
                    now
            },
        )
    }
}
