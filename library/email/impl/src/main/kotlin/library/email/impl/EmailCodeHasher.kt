package library.email.impl

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid

internal class EmailCodeHasher private constructor(private val key: SecretKeySpec) {

    fun codeHash(verificationId: Uuid, code: String): String = hmacHex("code:$verificationId:$code")

    fun matches(expectedHash: String, verificationId: Uuid, code: String): Boolean = MessageDigest.isEqual(
        expectedHash.toByteArray(),
        codeHash(verificationId, code).toByteArray(),
    )

    fun addressKey(email: String): String = hmacHex("address:${email.lowercase()}")

    private fun hmacHex(value: String): String = Mac.getInstance(ALGORITHM)
        .apply { init(key) }
        .doFinal(value.toByteArray())
        .toHexString()

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        const val MIN_SECRET_BYTES = 32

        fun fromSecret(secret: String): EmailCodeHasher {
            val bytes = secret.toByteArray()
            require(bytes.size >= MIN_SECRET_BYTES) { "The email code secret must be at least $MIN_SECRET_BYTES bytes" }
            return EmailCodeHasher(SecretKeySpec(bytes, ALGORITHM))
        }
    }
}
