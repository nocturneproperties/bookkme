package library.encryption.impl

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal object FieldCipher {
    private const val FORMAT_V1: Byte = 1
    private const val HEADER_LENGTH = 1
    private const val IV_LENGTH_BYTES = 12
    private val random = SecureRandom()

    fun encrypt(key: SecretKey, subjectId: Uuid, plaintext: String): String {
        return Base64.encode(encrypt(key, subjectId, plaintext.encodeToByteArray()))
    }

    fun decrypt(key: SecretKey, subjectId: Uuid, ciphertext: String): String {
        return decrypt(key, subjectId, Base64.decode(ciphertext)).decodeToString()
    }

    fun encrypt(key: SecretKey, subjectId: Uuid, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also(random::nextBytes)
        val sealed = AesGcm.seal(key, iv, plaintext, associatedData(subjectId))
        return byteArrayOf(FORMAT_V1) + iv + sealed
    }

    fun decrypt(key: SecretKey, subjectId: Uuid, bytes: ByteArray): ByteArray {
        val isKnownFormat = bytes.size > HEADER_LENGTH + IV_LENGTH_BYTES && bytes[0] == FORMAT_V1
        require(isKnownFormat) { "Not an encrypted field in a known format" }
        val iv = bytes.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + IV_LENGTH_BYTES)
        val sealed = bytes.copyOfRange(HEADER_LENGTH + IV_LENGTH_BYTES, bytes.size)
        return AesGcm.open(key, iv, sealed, associatedData(subjectId))
    }

    private fun associatedData(subjectId: Uuid): ByteArray {
        return byteArrayOf(FORMAT_V1) + subjectId.toByteArray()
    }
}

internal object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128

    fun seal(key: SecretKey, iv: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        return cipher(Cipher.ENCRYPT_MODE, key, iv, associatedData).doFinal(plaintext)
    }

    fun open(key: SecretKey, iv: ByteArray, sealed: ByteArray, associatedData: ByteArray): ByteArray {
        return cipher(Cipher.DECRYPT_MODE, key, iv, associatedData).doFinal(sealed)
    }

    private fun cipher(mode: Int, key: SecretKey, iv: ByteArray, associatedData: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            updateAAD(associatedData)
        }
    }
}
