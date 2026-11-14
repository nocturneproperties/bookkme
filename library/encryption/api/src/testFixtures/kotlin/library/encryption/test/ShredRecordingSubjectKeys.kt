package library.encryption.test

import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

class ShredRecordingSubjectKeys(private val isInTransaction: suspend () -> Boolean) : SubjectKeys {
    private val shreds = mutableListOf<Shred>()

    val shredded: Set<Uuid>
        get() = shreds.flatMap { it.subjectIds }.toSet()

    val shreddedInTransaction: Set<Uuid>
        get() = shreds.filter { it.inTransaction }.flatMap { it.subjectIds }.toSet()

    override suspend fun encryptor(subjectId: Uuid): Encryptor? {
        throw UnsupportedOperationException("ShredRecordingSubjectKeys only records shredding")
    }

    override suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor {
        throw UnsupportedOperationException("ShredRecordingSubjectKeys only records shredding")
    }

    override suspend fun isShredded(subjectId: Uuid): Boolean {
        return subjectId in shredded
    }

    override suspend fun shred(subjectId: Uuid) {
        shredAll(listOf(subjectId))
    }

    override suspend fun shredAll(subjectIds: Collection<Uuid>) {
        shreds += Shred(subjectIds.toSet(), isInTransaction())
    }

    private class Shred(val subjectIds: Set<Uuid>, val inTransaction: Boolean)
}
