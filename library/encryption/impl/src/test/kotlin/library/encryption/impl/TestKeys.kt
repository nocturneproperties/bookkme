package library.encryption.impl

import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

internal fun randomBase64Key(bytes: Int = 32): String {
    return Base64.encode(randomBytes(bytes))
}

internal fun randomSecretKey(): SecretKey {
    return SecretKeySpec(randomBytes(32), "AES")
}

private fun randomBytes(size: Int): ByteArray {
    return ByteArray(size).also(SecureRandom()::nextBytes)
}
