package library.encryption.impl

import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SubjectKeysImpl(
    private val dataSource: SubjectKeyDataSource,
    private val masterKey: MasterKey,
    private val random: SecureRandom,
    private val clock: Clock,
) : SubjectKeys {

    override suspend fun encryptor(subjectId: Uuid): Encryptor {
        val stored = dataSource.get(subjectId) ?: dataSource.insertIfAbsent(newKey(subjectId))
        val key = masterKey.unwrap(stored)
        return Encryptor { plaintext -> FieldCipher.encrypt(key, subjectId, plaintext) }
    }

    override suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor {
        val requested = subjectIds.toSet()
        val keys = if (requested.isEmpty()) {
            emptyMap()
        } else {
            dataSource.getAll(requested).associate { it.subjectId to masterKey.unwrap(it) }
        }
        return Decryptor { subjectId, ciphertext ->
            require(subjectId in requested) { "Subject $subjectId was not requested from this decryptor" }
            keys[subjectId]?.let { FieldCipher.decrypt(it, subjectId, ciphertext) }
        }
    }

    override suspend fun shred(subjectId: Uuid) {
        dataSource.delete(subjectId)
    }

    private fun newKey(subjectId: Uuid): StoredSubjectKey {
        val keyBytes = ByteArray(MasterKey.KEY_LENGTH_BYTES).also(random::nextBytes)
        val subjectKey = SecretKeySpec(keyBytes, MasterKey.ALGORITHM)
        return StoredSubjectKey(subjectId, masterKey.wrap(subjectId, subjectKey), masterKey.version, clock.now())
    }
}
