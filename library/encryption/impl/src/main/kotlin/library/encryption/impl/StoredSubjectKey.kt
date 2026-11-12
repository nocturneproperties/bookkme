package library.encryption.impl

import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class StoredSubjectKey(
    val subjectId: Uuid,
    val wrappedKey: ByteArray,
    val masterKeyVersion: Int,
    val createdAt: Instant,
) {
    companion object {
        const val WRAPPED_KEY_LENGTH = 60
    }
}
