package library.encryption.impl

import library.encryption.BlindIndex
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

internal class HmacBlindIndex private constructor(private val key: SecretKeySpec) : BlindIndex {

    override fun of(value: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return Base64.encode(mac.doFinal(value.encodeToByteArray()))
    }

    companion object {
        const val LENGTH = 44
        private const val ALGORITHM = "HmacSHA256"
        private const val MIN_KEY_LENGTH_BYTES = 32

        fun fromBase64(encoded: String): HmacBlindIndex {
            val bytes = Base64.decode(encoded)
            require(bytes.size >= MIN_KEY_LENGTH_BYTES) {
                "The blind index key must be at least $MIN_KEY_LENGTH_BYTES bytes, not ${bytes.size}"
            }
            return HmacBlindIndex(SecretKeySpec(bytes, ALGORITHM))
        }
    }
}
