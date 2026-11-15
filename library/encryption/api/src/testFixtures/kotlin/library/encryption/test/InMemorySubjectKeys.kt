package library.encryption.test

import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import library.encryption.decryptText
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class InMemorySubjectKeys : SubjectKeys {
    private val live = ConcurrentHashMap<Uuid, Uuid>()
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
        val keyId = live.computeIfAbsent(subjectId) { Uuid.random() }
        return KeyEncryptor(subjectId, keyId)
    }

    override suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor {
        val requested = subjectIds.toSet()
        return SubjectDecryptor(
            requested = requested,
            liveKeys = live.filterKeys { it in requested },
            shredded = shredded.filter { it in requested }.toSet(),
            tampered = tampered.toSet(),
        )
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

    private class KeyEncryptor(private val subjectId: Uuid, private val keyId: Uuid) : Encryptor {
        override fun encrypt(plaintext: String): String {
            return "${prefix(subjectId, keyId)}$plaintext"
        }

        override fun encrypt(plaintext: ByteArray): ByteArray {
            return prefix(subjectId, keyId).encodeToByteArray() + plaintext
        }
    }

    private class SubjectDecryptor(
        private val requested: Set<Uuid>,
        private val liveKeys: Map<Uuid, Uuid>,
        private val shredded: Set<Uuid>,
        private val tampered: Set<Uuid>,
    ) : Decryptor {
        override fun decrypt(subjectId: Uuid, ciphertext: String): Decrypted<String> {
            return decryptText(subjectId, ciphertext.encodeToByteArray())
        }

        override fun decrypt(subjectId: Uuid, ciphertext: ByteArray): Decrypted<ByteArray> {
            require(subjectId in requested) { "Subject $subjectId was not requested from this decryptor" }
            val keyId = liveKeys[subjectId]
            return when {
                keyId != null -> authenticated(subjectId, keyId, ciphertext)
                subjectId in shredded -> Decrypted.Shredded
                else -> Decrypted.Missing
            }
        }

        private fun authenticated(subjectId: Uuid, keyId: Uuid, ciphertext: ByteArray): Decrypted<ByteArray> {
            val prefix = prefix(subjectId, keyId).encodeToByteArray()
            val untouched = subjectId !in tampered && ciphertext.take(prefix.size) == prefix.toList()
            if (!untouched) return Decrypted.Missing
            return Decrypted.Plain(ciphertext.copyOfRange(prefix.size, ciphertext.size))
        }
    }

    private companion object {
        const val NIL_SUBJECT = "The nil subject has no key"

        fun prefix(subjectId: Uuid, keyId: Uuid): String {
            return "enc[$subjectId/$keyId]:"
        }
    }
}
