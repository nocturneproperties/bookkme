package library.email.impl

import com.bookk.core.domain.entity.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import library.email.DeleteExpiredSentEmails
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

internal class HostingerDeleteExpiredSentEmails(
    private val httpClient: HttpClient,
    private val apiToken: String,
    private val mailboxId: String,
    private val clock: Clock,
) : DeleteExpiredSentEmails {

    private val sentFolderUrl = "$HOSTINGER_MAIL_API_URL/mailboxes/$mailboxId/folders/$SENT_FOLDER/messages"

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val sentBefore = retentionCutoff()
        val deleted = deleteSentBefore(sentBefore)
        if (deleted > 0) logger.info("Deleted {} sent emails sent before {}", deleted, sentBefore)
    }

    private suspend fun deleteSentBefore(sentBefore: Instant): Int {
        var deleted = 0
        repeat(MAX_PAGES_PER_RUN) {
            val expired = oldestSent(sentBefore).filter { it.wasSentBefore(sentBefore) }.map { it.uid }
            if (expired.isEmpty()) return deleted
            delete(expired)
            deleted += expired.size
            if (expired.size < PAGE_SIZE) return deleted
        }
        return deleted
    }

    private suspend fun oldestSent(sentBefore: Instant): List<HostingerMessage> {
        val response = httpClient.post("$sentFolderUrl/search") {
            bearerAuth(apiToken)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            parameter("sort", "date")
            parameter("perPage", PAGE_SIZE)
            setBody(HostingerMessageSearch(before = searchDate(sentBefore)))
        }
        response.requireSuccess("search the sent folder")
        return response.body<HostingerMessagePage>().data
    }

    private suspend fun delete(uids: List<Long>) {
        val response = httpClient.post("$sentFolderUrl/delete") {
            bearerAuth(apiToken)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(HostingerMessageDeletion(uids))
        }
        response.requireSuccess("delete sent emails")
    }

    private fun retentionCutoff(): Instant {
        val now = clock.now().toJavaInstant().atOffset(ZoneOffset.UTC)
        return now.minusYears(RETENTION_YEARS).toInstant().toKotlinInstant()
    }

    private fun searchDate(sentBefore: Instant): String {
        val dayAfterAnyTimeZone = (sentBefore + SEARCH_MARGIN).toJavaInstant()
        return dayAfterAnyTimeZone.atOffset(ZoneOffset.UTC).toLocalDate().toString()
    }

    private fun HostingerMessage.wasSentBefore(sentBefore: Instant): Boolean {
        val sentAt = date?.let { Instant.parse(it) } ?: return false
        return sentAt <= sentBefore
    }

    private suspend fun HttpResponse.requireSuccess(action: String) {
        if (status.isSuccess()) return
        val refusal = runSuspendCatching { body<HostingerMailError>() }.getOrNull()
        throw IllegalStateException(
            "Hostinger Mail API refused to $action with HTTP ${status.value}, error ${refusal?.code}: ${refusal?.error}",
        )
    }

    private companion object {
        const val SENT_FOLDER = "INBOX.Sent"
        const val PAGE_SIZE = 100
        const val MAX_PAGES_PER_RUN = 500
        const val RETENTION_YEARS = 5L
        val SEARCH_MARGIN = 2.days
        val logger = LoggerFactory.getLogger(HostingerDeleteExpiredSentEmails::class.java)
    }
}
