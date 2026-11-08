package library.email

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class EmailVerificationStarted(
    @ProtoNumber(1) val verificationId: Uuid,
    @ProtoNumber(2) val expiresAt: Instant,
    @ProtoNumber(3) val resendAvailableAt: Instant,
) {
    companion object {
        fun stub(
            verificationId: Uuid = Uuid.random(),
            expiresAt: Instant = Instant.fromEpochMilliseconds(0),
            resendAvailableAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = EmailVerificationStarted(verificationId, expiresAt, resendAvailableAt)
    }
}
