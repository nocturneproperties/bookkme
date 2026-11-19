package library.email

interface DeleteExpiredSentEmails {
    suspend operator fun invoke(): Result<Unit>
}
