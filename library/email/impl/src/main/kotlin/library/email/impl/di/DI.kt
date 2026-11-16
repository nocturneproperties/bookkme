package library.email.impl.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import library.email.EmailCodeIssuer
import library.email.EmailCodeRedeemer
import library.email.EmailSender
import library.email.impl.EmailCodeHasher
import library.email.impl.EmailCodeIssuerImpl
import library.email.impl.EmailCodeMetrics
import library.email.impl.EmailCodeRedeemerImpl
import library.email.impl.EmailCodeStore
import library.email.impl.HostingerEmailSender
import library.email.impl.LoggingEmailSender
import library.email.impl.hostingerMailHttpClient
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.security.SecureRandom
import kotlin.time.Clock

private const val HOSTINGER_MAIL_TOKEN = "APPLICATION_HOSTINGER_MAIL_TOKEN"
private const val HOSTINGER_MAILBOX_ID = "APPLICATION_HOSTINGER_MAILBOX_ID"
private const val EMAIL_SENDER_NAME = "APPLICATION_EMAIL_SENDER_NAME"
private const val EMAIL_CODE_SECRET = "APPLICATION_EMAIL_CODE_SECRET"
private val hostingerMailClient = named("hostingerMailHttpClient")

fun emailModule(qualifier: Qualifier, environment: (String) -> String? = System::getenv) = module {
    scope(qualifier) {
        scoped<HttpClient>(hostingerMailClient) { hostingerMailHttpClient(CIO.create()) }
        scoped<EmailSender> {
            val apiToken = environment.require(HOSTINGER_MAIL_TOKEN)
            val senderName = environment.require(EMAIL_SENDER_NAME)
            if (apiToken == LoggingEmailSender.DEVELOPMENT_API_TOKEN) {
                LoggingEmailSender(senderName)
            } else {
                HostingerEmailSender(
                    httpClient = get(hostingerMailClient),
                    apiToken = apiToken,
                    mailboxId = environment.require(HOSTINGER_MAILBOX_ID),
                    senderName = senderName,
                )
            }
        }
        scoped<EmailCodeHasher> { environment.codeHasher() }
        scoped<EmailCodeStore> { EmailCodeStore(get(), get()) }
        scoped<EmailCodeMetrics> { EmailCodeMetrics(get()) }
        scoped<EmailCodeIssuer> { EmailCodeIssuerImpl(get(), get(), get(), SecureRandom(), Clock.System, get()) }
        scoped<EmailCodeRedeemer> { EmailCodeRedeemerImpl(get(), get(), get()) }
    }
}

private fun ((String) -> String?).require(variable: String): String =
    this(variable) ?: throw IllegalStateException("$variable is not set, so email can't be sent")

private fun ((String) -> String?).codeHasher(): EmailCodeHasher {
    val secret = require(EMAIL_CODE_SECRET)
    return runCatching { EmailCodeHasher.fromSecret(secret) }
        .getOrElse { throw IllegalStateException("$EMAIL_CODE_SECRET is too short to key the email code hash", it) }
}
