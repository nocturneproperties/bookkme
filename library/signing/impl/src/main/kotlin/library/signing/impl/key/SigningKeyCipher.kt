package library.signing.impl.key

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

internal object SigningKeyCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(plaintext: String, key: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key.toSecretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        return Base64.encode(iv + cipher.doFinal(plaintext.toByteArray()))
    }

    fun decrypt(encoded: String, key: String): String {
        val bytes = Base64.decode(encoded)
        val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key.toSecretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        return String(cipher.doFinal(ciphertext))
    }

    private fun String.toSecretKey() = SecretKeySpec(Base64.decode(this), "AES")
}
