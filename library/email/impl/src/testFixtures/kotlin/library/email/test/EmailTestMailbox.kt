package library.email.test

import com.bookk.core.data.cache.test.InMemoryCacheClient
import library.email.EmailCodeIssuer
import library.email.EmailCodeRedeemer
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import library.email.impl.EmailCodeHasher
import library.email.impl.EmailCodeIssuerImpl
import library.email.impl.EmailCodeRedeemerImpl
import library.email.impl.EmailCodeStore
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.TestTimeSource

class EmailTestMailbox(val timeSource: TestTimeSource = TestTimeSource()) {
    private val hasher = EmailCodeHasher.fromSecret("email-test-mailbox-secret-of-32-bytes")
    private val store = EmailCodeStore(InMemoryCacheClient(timeSource = timeSource), hasher)
    private val delivered = mutableListOf<EmailMessage>()
    var refusesDelivery = false

    private val sender = object : EmailSender {
        override suspend fun send(message: EmailMessage): Result<Unit> {
            if (refusesDelivery) return Result.failure(EmailSendError.Undeliverable())
            delivered += message
            return Result.success(Unit)
        }
    }

    val issuer: EmailCodeIssuer = EmailCodeIssuerImpl(store, hasher, sender, SecureRandom(), Clock.System)
    val redeemer: EmailCodeRedeemer = EmailCodeRedeemerImpl(store, hasher)

    val sent: List<EmailMessage> get() = delivered.toList()

    fun codeSentTo(email: String): String {
        val message = delivered.last { it.to == email }
        return requireNotNull(CODE.find(message.content.textBody)) { "No code in the email sent to $email" }.value
    }

    private companion object {
        val CODE = Regex("\\b\\d{${EmailCodeIssuerImpl.CODE_LENGTH}}\\b")
    }
}
