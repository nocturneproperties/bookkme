package library.encryption.test

import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.AEADBadTagException
import kotlin.uuid.Uuid

class InMemorySubjectKeys : SubjectKeys {
    private val live = ConcurrentHashMap.newKeySet<Uuid>()
    private val shredded = ConcurrentHashMap.newKeySet<Uuid>()
    private val tampered = ConcurrentHashMap.newKeySet<Uuid>()

    fun loseKey(subjectId: Uuid) {
        live -= subjectId
    }

    fun tamperWithCiphertextsOf(subjectId: Uuid) {
        tampered += subjectId
    }

    override suspend fun encryptor(subjectId: Uuid): Encryptor? {
        require(subjectId != Uuid.NIL) { NIL_SUBJECT }
        if (subjectId in shredded) return null
        live += subjectId
        return SubjectEncryptor(subjectId)
    }

    override suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor {
        return SubjectDecryptor(live.toSet(), shredded.toSet(), tampered.toSet())
    }

    override suspend fun isShredded(subjectId: Uuid): Boolean {
        return subjectId in shredded
    }

    override suspend fun shred(subjectId: Uuid) {
        shredAll(listOf(subjectId))
    }

    override suspend fun shredAll(subjectIds: Collection<Uuid>) {
        require(Uuid.NIL !in subjectIds) { NIL_SUBJECT }
        live -= subjectIds.toSet()
        shredded += subjectIds
    }

    private class SubjectEncryptor(private val subjectId: Uuid) : Encryptor {
        override fun encrypt(plaintext: String): String {
            return "${prefix(subjectId)}$plaintext"
        }

        override fun encrypt(plaintext: ByteArray): ByteArray {
            return prefix(subjectId).encodeToByteArray() + plaintext
        }
    }

    private class SubjectDecryptor(
        private val live: Set<Uuid>,
        private val shredded: Set<Uuid>,
        private val tampered: Set<Uuid>,
    ) : Decryptor {
        override fun decrypt(subjectId: Uuid, ciphertext: String): Decrypted<String> {
            return when (val decrypted = decrypt(subjectId, ciphertext.encodeToByteArray())) {
                is Decrypted.Plain -> Decrypted.Plain(decrypted.value.decodeToString())
                Decrypted.Shredded -> Decrypted.Shredded
                Decrypted.Missing -> Decrypted.Missing
            }
        }

        override fun decrypt(subjectId: Uuid, ciphertext: ByteArray): Decrypted<ByteArray> {
            return when (subjectId) {
                in live -> Decrypted.Plain(authenticated(subjectId, ciphertext))
                in shredded -> Decrypted.Shredded
                else -> Decrypted.Missing
            }
        }

        private fun authenticated(subjectId: Uuid, ciphertext: ByteArray): ByteArray {
            val prefix = prefix(subjectId).encodeToByteArray()
            val untouched = subjectId !in tampered && ciphertext.take(prefix.size) == prefix.toList()
            if (!untouched) throw AEADBadTagException("Tag mismatch")
            return ciphertext.copyOfRange(prefix.size, ciphertext.size)
        }
    }

    private companion object {
        const val NIL_SUBJECT = "The nil subject has no key"

        fun prefix(subjectId: Uuid): String {
            return "enc[$subjectId]:"
        }
    }
}
