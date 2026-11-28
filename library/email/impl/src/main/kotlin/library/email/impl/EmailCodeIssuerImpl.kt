package library.email.impl

import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import library.email.EmailCodeError
import library.email.EmailCodeIssuer
import library.email.EmailContent
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import library.email.EmailVerificationStarted
import library.email.PlaceholderEmail
import java.util.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class EmailCodeIssuerImpl(
    private val store: EmailCodeStore,
    private val hasher: EmailCodeHasher,
    private val sender: EmailSender,
    private val random: Random,
    private val clock: Clock,
    private val metrics: EmailCodeMetrics,
) : EmailCodeIssuer {

    override suspend fun issue(
        email: String,
        purpose: String,
        ownerId: Uuid,
        render: (code: String) -> EmailContent,
    ): Result<EmailVerificationStarted> = runSuspendCatching {
        if (PlaceholderEmail.isPlaceholder(email)) throw EmailSendError.Undeliverable()
        if (!store.claimCooldown(email, RESEND_COOLDOWN)) throw EmailCodeError.ResendTooSoon()
        runCleaningUpOnFailure(cleanUp = { store.releaseCooldown(email) }) {
            val sendsInWindow = store.countSend(email, SEND_LIMIT_WINDOW)
            if (sendsInWindow == MAX_SENDS_PER_WINDOW + 1L) metrics.sendLimitReached()
            if (sendsInWindow > MAX_SENDS_PER_WINDOW) throw EmailCodeError.ResendTooSoon()
            val verificationId = Uuid.random()
            val code = newEmailCode(random)
            val session = EmailCodeSession(ownerId, email, purpose, hasher.codeHash(verificationId, code))
            store.saveSession(verificationId, session, CODE_LIFETIME)
            sender.send(EmailMessage(to = email, content = render(code))).getOrThrow()
            store.markLatest(ownerId, verificationId, CODE_LIFETIME)
            val now = clock.now()
            EmailVerificationStarted(
                verificationId,
                expiresAt = now + CODE_LIFETIME,
                resendAvailableAt =
                now + RESEND_COOLDOWN,
            )
        }
    }

    companion object {
        val CODE_LIFETIME = 10.minutes
        val RESEND_COOLDOWN = 60.seconds
        val SEND_LIMIT_WINDOW = 1.hours
        const val MAX_SENDS_PER_WINDOW = 5
        const val CODE_LENGTH = 6
    }
}
