package library.encryption.impl

import kotlin.time.Instant
import kotlin.uuid.Uuid

internal sealed interface StoredSubjectKey {
    val subjectId: Uuid

    class Live(
        override val subjectId: Uuid,
        val wrappedKey: ByteArray,
        val masterKeyVersion: Int,
        val createdAt: Instant,
    ) : StoredSubjectKey

    class Shredded(override val subjectId: Uuid, val shreddedAt: Instant) : StoredSubjectKey

    companion object {
        const val WRAPPED_KEY_LENGTH = 60
    }
}
