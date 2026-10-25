package library.signing

import kotlin.time.Instant
import kotlin.uuid.Uuid

class SigningKey(
    val id: Uuid,
    val publicKeyPem: String,
    val privateKeyPem: String,
    val status: SigningKeyStatus,
    val createdAt: Instant,
    val retiredAt: Instant?
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            publicKeyPem: String = "public",
            privateKeyPem: String = "private",
            status: SigningKeyStatus = SigningKeyStatus.ACTIVE,
            createdAt: Instant = Instant.fromEpochMilliseconds(0),
            retiredAt: Instant? = null
        ) = SigningKey(id, publicKeyPem, privateKeyPem, status, createdAt, retiredAt)
    }
}
