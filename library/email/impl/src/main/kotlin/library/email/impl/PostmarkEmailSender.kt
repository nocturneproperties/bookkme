package library.email.impl

import com.bookk.core.domain.entity.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import kotlin.time.Duration.Companion.milliseconds

internal class PostmarkEmailSender(
    private val httpClient: HttpClient,
    private val serverToken: String,
    private val from: String,
) : EmailSender {

    override suspend fun send(message: EmailMessage): Result<Unit> = runSuspendCatching {
        val response = httpClient.post(EMAIL_URL) {
            header(SERVER_TOKEN_HEADER, serverToken)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(message.toPostmark())
        }
        if (response.status.isSuccess()) return@runSuspendCatching
        val refusal = response.refusal()
        if (response.status == HttpStatusCode.UnprocessableEntity && refusal?.errorCode in UNDELIVERABLE_ERROR_CODES) throw EmailSendError.Undeliverable()
        throw IllegalStateException("Postmark refused the email with HTTP ${response.status.value}, error ${refusal?.errorCode}: ${refusal?.message}")
    }

    private fun EmailMessage.toPostmark() = PostmarkEmail(
        from = from,
        to = to,
        subject = content.subject,
        textBody = content.textBody,
        htmlBody = content.htmlBody,
        messageStream = TRANSACTIONAL_STREAM,
        tag = content.tag,
        trackOpens = false,
        trackLinks = NO_LINK_TRACKING,
    )

    private suspend fun HttpResponse.refusal(): PostmarkResponse? = runSuspendCatching { body<PostmarkResponse>() }.getOrNull()

    private companion object {
        const val EMAIL_URL = "https://api.postmarkapp.com/email"
        const val SERVER_TOKEN_HEADER = "X-Postmark-Server-Token"
        const val TRANSACTIONAL_STREAM = "outbound"
        const val NO_LINK_TRACKING = "None"
        const val INVALID_EMAIL_REQUEST = 300
        const val INACTIVE_RECIPIENT = 406
        val UNDELIVERABLE_ERROR_CODES = setOf(INVALID_EMAIL_REQUEST, INACTIVE_RECIPIENT)
    }
}

private const val MAX_RETRIES = 2

internal fun postmarkHttpClient(
    engine: HttpClientEngine,
    retryDelay: suspend (Long) -> Unit = { delay(it.milliseconds) },
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpRequestRetry) {
        retryIf(MAX_RETRIES) { _, response -> response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500 }
        retryOnException(MAX_RETRIES, retryOnTimeout = true)
        exponentialDelay()
        delay(retryDelay)
    }
}
