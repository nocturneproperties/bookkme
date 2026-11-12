package library.encryption.impl

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal class MasterKey private constructor(val version: Int, private val key: SecretKey) {

    fun wrap(subjectId: Uuid, subjectKey: SecretKey): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also(random::nextBytes)
        return iv + AesGcm.seal(key, iv, subjectKey.encoded, associatedData(subjectId))
    }

    fun unwrap(stored: StoredSubjectKey): SecretKey {
        check(stored.masterKeyVersion == version) {
            val storedVersion = stored.masterKeyVersion
            "The key of subject ${stored.subjectId} is wrapped by master key version $storedVersion, not $version"
        }
        val iv = stored.wrappedKey.copyOfRange(0, IV_LENGTH_BYTES)
        val sealed = stored.wrappedKey.copyOfRange(IV_LENGTH_BYTES, stored.wrappedKey.size)
        return SecretKeySpec(AesGcm.open(key, iv, sealed, associatedData(stored.subjectId)), ALGORITHM)
    }

    private fun associatedData(subjectId: Uuid): ByteArray {
        val versionBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(version).array()
        return subjectId.toByteArray() + versionBytes
    }

    companion object {
        const val KEY_LENGTH_BYTES = 32
        const val ALGORITHM = "AES"
        private const val IV_LENGTH_BYTES = 12
        private val random = SecureRandom()

        fun fromBase64(version: Int, encoded: String): MasterKey {
            val bytes = Base64.decode(encoded)
            require(bytes.size == KEY_LENGTH_BYTES) {
                "The master key must be $KEY_LENGTH_BYTES bytes, not ${bytes.size}"
            }
            return MasterKey(version, SecretKeySpec(bytes, ALGORITHM))
        }
    }
}
