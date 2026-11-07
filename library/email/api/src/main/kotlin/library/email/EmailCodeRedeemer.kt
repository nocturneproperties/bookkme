package library.email

import kotlin.uuid.Uuid

interface EmailCodeRedeemer {
    suspend fun <T> redeem(verificationId: Uuid, code: String, ownerId: Uuid, action: suspend (VerifiedEmail) -> T): Result<T>
}
