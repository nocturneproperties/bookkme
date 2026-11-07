package library.email.impl

import java.security.MessageDigest
import java.util.Random

internal fun newEmailCode(random: Random): String = buildString {
    repeat(EmailCodeIssuerImpl.CODE_LENGTH) {
        append(random.nextInt(10))
    }
}

internal fun emailCodeHash(verificationId: String, code: String): String = sha256Hex("$verificationId:$code")

internal fun emailCodeMatches(expectedHash: String, verificationId: String, code: String): Boolean = MessageDigest.isEqual(
    expectedHash.toByteArray(),
    emailCodeHash(verificationId, code).toByteArray(),
)

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .toHexString()
