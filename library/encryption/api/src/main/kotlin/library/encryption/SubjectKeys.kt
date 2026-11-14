package library.encryption

import kotlin.uuid.Uuid

interface SubjectKeys {
    suspend fun encryptor(subjectId: Uuid): Encryptor?

    suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor

    suspend fun isShredded(subjectId: Uuid): Boolean

    suspend fun shred(subjectId: Uuid)

    suspend fun shredAll(subjectIds: Collection<Uuid>)
}
