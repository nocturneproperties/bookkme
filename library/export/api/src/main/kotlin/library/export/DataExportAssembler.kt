package library.export

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface DataExportAssembler {
    suspend fun assemble(ownerId: Uuid): String

    suspend fun announceReady(ownerId: Uuid, expiresAt: Instant)
}
