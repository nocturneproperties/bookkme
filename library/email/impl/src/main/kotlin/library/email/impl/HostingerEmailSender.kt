package library.email.impl

import com.bookk.core.domain.entity.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender

internal class HostingerEmailSender(
    private val httpClient: HttpClient,
    private val apiToken: String,
    private val mailboxId: String,
    private val senderName: String,
) : EmailSender {

    override suspend fun send(message: EmailMessage): Result<Unit> = runSuspendCatching {
        val response = httpClient.post("$HOSTINGER_MAIL_API_URL/mailboxes/$mailboxId/send") {
            bearerAuth(apiToken)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(message.toHostinger())
        }
        if (response.status.isSuccess()) return@runSuspendCatching
        val refusal = response.refusal()
        if (response.status == HttpStatusCode.UnprocessableEntity && refusal?.refusesRecipient() == true) {
            throw EmailSendError.Undeliverable()
        }
        throw IllegalStateException(
            "Hostinger Mail API refused the email with HTTP ${response.status.value}, " +
                "error ${refusal?.code}: ${refusal?.error}",
        )
    }

    private fun EmailMessage.toHostinger(): HostingerMailRequest {
        return HostingerMailRequest(
            to = listOf(to),
            displayName = senderName,
            subject = content.subject,
            text = content.textBody,
            html = content.htmlBody,
        )
    }

    private fun HostingerMailError.refusesRecipient(): Boolean {
        return code == VALIDATION_FAILED && refusedFields.any { it == RECIPIENTS || it.startsWith("$RECIPIENTS.") }
    }

    private suspend fun HttpResponse.refusal(): HostingerMailError? = runSuspendCatching {
        body<HostingerMailError>()
    }.getOrNull()

    private companion object {
        const val VALIDATION_FAILED = "ERR_VALIDATION_FAILED"
        const val RECIPIENTS = "to"
    }
}
