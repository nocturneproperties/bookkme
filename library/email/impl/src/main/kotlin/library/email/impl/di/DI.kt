package library.email.impl.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import library.email.EmailCodeIssuer
import library.email.EmailCodeRedeemer
import library.email.EmailSender
import library.email.impl.EmailCodeHasher
import library.email.impl.EmailCodeIssuerImpl
import library.email.impl.EmailCodeRedeemerImpl
import library.email.impl.EmailCodeStore
import library.email.impl.LoggingEmailSender
import library.email.impl.PostmarkEmailSender
import library.email.impl.postmarkHttpClient
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.security.SecureRandom
import kotlin.time.Clock

private const val POSTMARK_SERVER_TOKEN = "APPLICATION_POSTMARK_SERVER_TOKEN"
private const val EMAIL_FROM = "APPLICATION_EMAIL_FROM"
private const val EMAIL_CODE_SECRET = "APPLICATION_EMAIL_CODE_SECRET"
private val postmarkClient = named("postmarkHttpClient")

fun emailModule(qualifier: Qualifier, environment: (String) -> String? = System::getenv) = module {
    scope(qualifier) {
        scoped<HttpClient>(postmarkClient) { postmarkHttpClient(CIO.create()) }
        scoped<EmailSender> {
            val serverToken = environment.require(POSTMARK_SERVER_TOKEN)
            val from = environment.require(EMAIL_FROM)
            if (serverToken == LoggingEmailSender.DEVELOPMENT_SERVER_TOKEN) {
                LoggingEmailSender(from)
            } else {
                PostmarkEmailSender(httpClient = get(postmarkClient), serverToken = serverToken, from = from)
            }
        }
        scoped<EmailCodeHasher> { environment.codeHasher() }
        scoped<EmailCodeStore> { EmailCodeStore(get(), get()) }
        scoped<EmailCodeIssuer> { EmailCodeIssuerImpl(get(), get(), get(), SecureRandom(), Clock.System) }
        scoped<EmailCodeRedeemer> { EmailCodeRedeemerImpl(get(), get()) }
    }
}

private fun ((String) -> String?).require(variable: String): String =
    this(variable) ?: throw IllegalStateException("$variable is not set, so email can't be sent")

private fun ((String) -> String?).codeHasher(): EmailCodeHasher {
    val secret = require(EMAIL_CODE_SECRET)
    return runCatching { EmailCodeHasher.fromSecret(secret) }
        .getOrElse { throw IllegalStateException("$EMAIL_CODE_SECRET is too short to key the email code hash", it) }
}
