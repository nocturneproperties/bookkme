package library.email

import kotlin.uuid.Uuid

interface EmailCodeIssuer {
    suspend fun issue(email: String, purpose: String, ownerId: Uuid, render: (code: String) -> EmailContent): Result<EmailVerificationStarted>
}
