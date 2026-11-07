package library.export.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import library.export.BuildDataExports
import library.export.DataExportAssembler
import library.export.DataExports
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = LoggerFactory.getLogger(BuildDataExportsImpl::class.java)

internal class BuildDataExportsImpl(
    private val transactionManager: TransactionManager,
    private val dataSource: DataExportDataSource,
    private val assembler: DataExportAssembler,
    private val clock: Clock,
) : BuildDataExports {

    private class Claim(val ownerId: Uuid, val id: Uuid)

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        queuedOwnerIds().forEach { ownerId ->
            runSuspendCatching { build(ownerId) }
                .onFailure { logger.error("Data export of $ownerId could not be recorded: ${it.message}", it) }
        }
    }

    private suspend fun queuedOwnerIds(): List<Uuid> {
        val now = clock.now()
        return transactionManager.transactionOrThrow {
            dataSource.failAbandoned(now, documentExpiry(now))
            dataSource.claimable(now)
        }
    }

    private suspend fun build(ownerId: Uuid) {
        val claim = claim(ownerId) ?: return
        runSuspendCatching { claim.store(assembler.assemble(ownerId).gzipped()) }
            .onFailure { failure ->
                logger.warn("Data export of $ownerId failed: ${failure.message}", failure)
                claim.giveBack()
            }
    }

    private suspend fun claim(ownerId: Uuid): Claim? {
        val claim = Claim(ownerId, id = Uuid.random())
        val claimedAt = clock.now()
        val claimed = transactionManager.transactionOrThrow { dataSource.claim(ownerId, claim.id, claimedAt, claimedAt + DataExports.BUILD_LEASE) }
        return claim.takeIf { claimed }
    }

    private suspend fun Claim.store(document: ByteArray) {
        val finishedAt = clock.now()
        val expiresAt = documentExpiry(finishedAt)
        transactionManager.transactionOrThrow {
            val stillHeld = dataSource.complete(ownerId, id, document, finishedAt, expiresAt)
            if (stillHeld) assembler.announceReady(ownerId, expiresAt)
        }
    }

    private suspend fun Claim.giveBack() {
        val failedAt = clock.now()
        transactionManager.transactionOrThrow { dataSource.giveBack(ownerId, id, failedAt, documentExpiry(failedAt)) }
    }
}

private fun documentExpiry(from: Instant): Instant = from + DataExports.DOCUMENT_LIFETIME

private fun String.gzipped(): ByteArray = ByteArrayOutputStream()
    .also { bytes -> GZIPOutputStream(bytes).use { it.write(encodeToByteArray()) } }
    .toByteArray()
