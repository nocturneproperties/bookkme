package library.export.impl

import com.bookk.core.data.DataSource
import library.export.DataExports
import library.export.impl.orm.DataExportTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DataExportDataSource : DataSource() {

    suspend fun get(ownerId: Uuid): DataExportRecord? = dbQuery {
        DataExportTable.selectAll().where { DataExportTable.ownerId eq ownerId }.singleOrNull()?.toRecord()
    }

    suspend fun getForUpdate(ownerId: Uuid): DataExportRecord? = dbQuery {
        DataExportTable.selectAll().where { DataExportTable.ownerId eq ownerId }.forUpdate().singleOrNull()?.toRecord()
    }

    suspend fun insert(ownerId: Uuid, requestedAt: Instant) = dbQuery<Unit> {
        DataExportTable.insert {
            it[this.ownerId] = ownerId
            it[state] = DataExportState.PENDING
            it[this.requestedAt] = requestedAt
            it[attempts] = 0
        }
    }

    suspend fun restart(ownerId: Uuid, requestedAt: Instant) = dbQuery<Unit> {
        DataExportTable.update({ DataExportTable.ownerId eq ownerId }) {
            it[state] = DataExportState.PENDING
            it[this.requestedAt] = requestedAt
            it[attempts] = 0
            it[finishedAt] = null
            it[expiresAt] = null
            it[document] = null
            it[keyId] = null
            it.clearClaim()
        }
    }

    suspend fun claimable(now: Instant): List<Uuid> = dbQuery {
        DataExportTable.select(DataExportTable.ownerId)
            .where { claimableAt(now) }
            .orderBy(DataExportTable.requestedAt, SortOrder.ASC)
            .limit(CLAIMABLE_BATCH)
            .map { it[DataExportTable.ownerId] }
    }

    suspend fun claim(ownerId: Uuid, claimId: Uuid, now: Instant, until: Instant): Boolean = dbQuery {
        DataExportTable.update({ (DataExportTable.ownerId eq ownerId) and claimableAt(now) }) {
            it[state] = DataExportState.RUNNING
            it[this.claimId] = claimId
            it[claimedUntil] = until
            it[attempts] = attempts + 1
        } > 0
    }

    suspend fun complete(
        ownerId: Uuid,
        claimId: Uuid,
        document: ByteArray,
        keyId: Uuid,
        finishedAt: Instant,
        expiresAt: Instant,
    ): Boolean = dbQuery {
        DataExportTable.update({ heldBy(ownerId, claimId) }) {
            it[state] = DataExportState.READY
            it[this.document] = ExposedBlob(document)
            it[this.keyId] = keyId
            it[this.finishedAt] = finishedAt
            it[this.expiresAt] = expiresAt
            it.clearClaim()
        } > 0
    }

    suspend fun giveBack(ownerId: Uuid, claimId: Uuid, failedAt: Instant, expiresAt: Instant) = dbQuery<Unit> {
        val requeued = DataExportTable.update({
            heldBy(ownerId, claimId) and
                (DataExportTable.attempts less DataExports.MAX_BUILD_ATTEMPTS)
        }) {
            it[state] = DataExportState.PENDING
            it.clearClaim()
        }
        if (requeued == 0) {
            DataExportTable.update({ heldBy(ownerId, claimId) }) { it.markFailed(failedAt, expiresAt) }
        }
    }

    suspend fun failAbandoned(now: Instant, expiresAt: Instant) = dbQuery<Unit> {
        DataExportTable.update({
            (DataExportTable.state eq DataExportState.RUNNING) and
                (DataExportTable.claimedUntil less now) and
                (DataExportTable.attempts greaterEq DataExports.MAX_BUILD_ATTEMPTS)
        }) { it.markFailed(now, expiresAt) }
    }

    suspend fun document(ownerId: Uuid, now: Instant): StoredDataExportDocument? = dbQuery {
        val row = DataExportTable.select(DataExportTable.document, DataExportTable.keyId)
            .where {
                (DataExportTable.ownerId eq ownerId) and
                    (DataExportTable.state eq DataExportState.READY) and
                    (DataExportTable.expiresAt greater now)
            }
            .singleOrNull()
        row?.get(DataExportTable.document)?.let { StoredDataExportDocument(it.bytes, row[DataExportTable.keyId]) }
    }

    suspend fun delete(ownerIds: Set<Uuid>): List<Uuid> {
        if (ownerIds.isEmpty()) return emptyList()
        return dbQuery {
            val existing = DataExportTable.select(DataExportTable.ownerId, DataExportTable.keyId)
                .where { DataExportTable.ownerId inList ownerIds }
                .forUpdate()
                .toList()
            if (existing.isNotEmpty()) {
                DataExportTable.deleteWhere {
                    DataExportTable.ownerId inList
                        existing.map { it[DataExportTable.ownerId] }
                }
            }
            existing.mapNotNull { it[DataExportTable.keyId] }
        }
    }

    suspend fun deleteExpired(now: Instant): List<Uuid> = dbQuery {
        val expired = DataExportTable.select(DataExportTable.ownerId, DataExportTable.keyId)
            .where { DataExportTable.expiresAt lessEq now }
            .forUpdate()
            .toList()
        val ownerIds = expired.map { it[DataExportTable.ownerId] }
        if (ownerIds.isNotEmpty()) DataExportTable.deleteWhere { DataExportTable.ownerId inList ownerIds }
        expired.mapNotNull { it[DataExportTable.keyId] }
    }

    private fun claimableAt(
        now: Instant,
    ): Op<Boolean> = (DataExportTable.attempts less DataExports.MAX_BUILD_ATTEMPTS) and (
        (DataExportTable.state eq DataExportState.PENDING) or
            ((DataExportTable.state eq DataExportState.RUNNING) and (DataExportTable.claimedUntil less now))
        )

    private fun heldBy(ownerId: Uuid, claimId: Uuid): Op<Boolean> = (DataExportTable.ownerId eq ownerId) and
        (DataExportTable.state eq DataExportState.RUNNING) and
        (DataExportTable.claimId eq claimId)

    private fun UpdateStatement.clearClaim() {
        this[DataExportTable.claimId] = null
        this[DataExportTable.claimedUntil] = null
    }

    private fun UpdateStatement.markFailed(failedAt: Instant, expiresAt: Instant) {
        this[DataExportTable.state] = DataExportState.FAILED
        this[DataExportTable.finishedAt] = failedAt
        this[DataExportTable.expiresAt] = expiresAt
        clearClaim()
    }

    private fun ResultRow.toRecord() = DataExportRecord(
        ownerId = this[DataExportTable.ownerId],
        state = this[DataExportTable.state],
        requestedAt = this[DataExportTable.requestedAt],
        attempts = this[DataExportTable.attempts],
        expiresAt = this[DataExportTable.expiresAt],
        keyId = this[DataExportTable.keyId],
    )

    private companion object {
        const val CLAIMABLE_BATCH = 20
    }
}
