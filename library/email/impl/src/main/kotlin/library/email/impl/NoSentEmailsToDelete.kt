package library.email.impl

import library.email.DeleteExpiredSentEmails

internal object NoSentEmailsToDelete : DeleteExpiredSentEmails {
    override suspend fun invoke(): Result<Unit> {
        return Result.success(Unit)
    }
}
