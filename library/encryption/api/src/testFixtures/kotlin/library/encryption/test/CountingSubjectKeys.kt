package library.encryption.test

import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

class CountingSubjectKeys(private val keys: SubjectKeys = InMemorySubjectKeys()) : SubjectKeys by keys {
    private val encryptorRequests = mutableListOf<Uuid>()
    private val decryptorRequests = mutableListOf<Set<Uuid>>()

    val encryptorsRequested: List<Uuid>
        get() {
            return encryptorRequests.toList()
        }

    val decryptorsRequested: List<Set<Uuid>>
        get() {
            return decryptorRequests.toList()
        }

    fun resetCounts() {
        encryptorRequests.clear()
        decryptorRequests.clear()
    }

    override suspend fun encryptor(subjectId: Uuid): Encryptor? {
        encryptorRequests += subjectId
        return keys.encryptor(subjectId)
    }

    override suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor {
        decryptorRequests += subjectIds.toSet()
        return keys.decryptor(subjectIds)
    }
}
