package library.email.impl

import library.email.EmailMessage
import library.email.EmailSender
import org.slf4j.LoggerFactory

internal class LoggingEmailSender(private val from: String) : EmailSender {

    init {
        logger.warn(
            "Email delivery is disabled by the development Postmark token: every email, codes included, is written to this log instead of being sent",
        )
    }

    override suspend fun send(message: EmailMessage): Result<Unit> {
        logger.info(
            "Email not sent from {} to {}, subject \"{}\":\n{}",
            from,
            message.to,
            message.content.subject,
            message.content.textBody,
        )
        return Result.success(Unit)
    }

    companion object {
        const val DEVELOPMENT_SERVER_TOKEN = "development_token"
        private val logger = LoggerFactory.getLogger(LoggingEmailSender::class.java)
    }
}
