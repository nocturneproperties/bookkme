package library.encryption.impl

import library.encryption.Decrypted
import library.encryption.Decryptor
import library.encryption.Encryptor
import library.encryption.SubjectKeys
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SubjectKeysImpl(
    private val dataSource: SubjectKeyDataSource,
    private val masterKey: MasterKey,
    private val random: SecureRandom,
    private val clock: Clock,
) : SubjectKeys {

    override suspend fun encryptor(subjectId: Uuid): Encryptor? {
        val stored = dataSource.get(subjectId) ?: dataSource.insertIfAbsent(newKey(subjectId))
        return when (stored) {
            is StoredSubjectKey.Live -> SubjectEncryptor(subjectId, masterKey.unwrap(stored))
            is StoredSubjectKey.Shredded -> null
        }
    }

    override suspend fun decryptor(subjectIds: Collection<Uuid>): Decryptor {
        val requested = subjectIds.toSet()
        val stored = if (requested.isEmpty()) emptyList() else dataSource.getAll(requested)
        val liveKeys = stored.filterIsInstance<StoredSubjectKey.Live>().associate {
            it.subjectId to masterKey.unwrap(it)
        }
        val shredded = stored.filterIsInstance<StoredSubjectKey.Shredded>().map { it.subjectId }.toSet()
        return SubjectDecryptor(requested, liveKeys, shredded)
    }

    override suspend fun shred(subjectId: Uuid) {
        dataSource.shred(subjectId, clock.now())
    }

    private fun newKey(subjectId: Uuid): StoredSubjectKey.Live {
        val keyBytes = ByteArray(MasterKey.KEY_LENGTH_BYTES).also(random::nextBytes)
        val subjectKey = SecretKeySpec(keyBytes, MasterKey.ALGORITHM)
        return StoredSubjectKey.Live(subjectId, masterKey.wrap(subjectId, subjectKey), masterKey.version, clock.now())
    }
}

private class SubjectEncryptor(private val subjectId: Uuid, private val key: SecretKey) : Encryptor {

    override fun encrypt(plaintext: String): String {
        return FieldCipher.encrypt(key, subjectId, plaintext)
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        return FieldCipher.encrypt(key, subjectId, plaintext)
    }
}

private class SubjectDecryptor(
    private val requested: Set<Uuid>,
    private val liveKeys: Map<Uuid, SecretKey>,
    private val shredded: Set<Uuid>,
) : Decryptor {

    override fun decrypt(subjectId: Uuid, ciphertext: String): Decrypted<String> {
        return withKey(subjectId) { FieldCipher.decrypt(it, subjectId, ciphertext) }
    }

    override fun decrypt(subjectId: Uuid, ciphertext: ByteArray): Decrypted<ByteArray> {
        return withKey(subjectId) { FieldCipher.decrypt(it, subjectId, ciphertext) }
    }

    private fun <T> withKey(subjectId: Uuid, decrypt: (SecretKey) -> T): Decrypted<T> {
        require(subjectId in requested) { "Subject $subjectId was not requested from this decryptor" }
        val key = liveKeys[subjectId]
        return when {
            key != null -> Decrypted.Plain(decrypt(key))
            subjectId in shredded -> Decrypted.Shredded
            else -> Decrypted.Missing
        }
    }
}
