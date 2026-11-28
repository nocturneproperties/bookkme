package library.email.impl

import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import library.email.PlaceholderEmail

internal class PlaceholderRefusingEmailSender(val delegate: EmailSender) : EmailSender {

    override suspend fun send(message: EmailMessage): Result<Unit> {
        if (PlaceholderEmail.isPlaceholder(message.to)) return Result.failure(EmailSendError.Undeliverable())
        return delegate.send(message)
    }
}
