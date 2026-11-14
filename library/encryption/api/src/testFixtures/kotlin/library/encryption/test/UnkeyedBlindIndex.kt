package library.encryption.test

import library.encryption.BlindIndex
import java.security.MessageDigest
import kotlin.io.encoding.Base64

class UnkeyedBlindIndex : BlindIndex {
    override fun of(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
        return Base64.encode(digest)
    }
}
