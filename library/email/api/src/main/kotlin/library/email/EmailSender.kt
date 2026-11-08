package library.email

interface EmailSender {
    suspend fun send(message: EmailMessage): Result<Unit>
}
