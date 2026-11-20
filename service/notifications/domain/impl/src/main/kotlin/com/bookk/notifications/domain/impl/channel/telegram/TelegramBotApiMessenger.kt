package com.bookk.notifications.domain.impl.channel.telegram

import com.bookk.core.domain.entity.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class TelegramBotApiMessenger(
    private val httpClient: HttpClient,
    private val botToken: String,
) : TelegramMessenger {

    override suspend fun send(chatId: Long, text: String): TelegramDelivery {
        val response = post(SendMessageRequest(chatId = chatId, text = text.take(MAX_TEXT_LENGTH)))
        if (response.status.isSuccess()) return TelegramDelivery.SENT
        if (response.status == HttpStatusCode.Forbidden) return TelegramDelivery.CHAT_UNREACHABLE
        throw IllegalStateException(
            "Telegram Bot API refused the message with HTTP ${response.status.value}: ${response.description()}",
        )
    }

    private suspend fun post(request: SendMessageRequest): HttpResponse {
        return runSuspendCatching {
            httpClient.post("$TELEGRAM_BOT_API_URL/bot$botToken/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.getOrElse { failure ->
            val reason = failure.message?.replace(botToken, REDACTED_TOKEN)
            throw IllegalStateException("Telegram Bot API request failed with ${failure::class.simpleName}: $reason")
        }
    }

    private suspend fun HttpResponse.description(): String? {
        return runSuspendCatching { body<TelegramBotApiRefusal>().description }.getOrNull()
    }

    @Serializable
    private class SendMessageRequest(
        @SerialName("chat_id") val chatId: Long,
        @SerialName("text") val text: String,
    )

    @Serializable
    private class TelegramBotApiRefusal(
        @SerialName("description") val description: String? = null,
    )

    private companion object {
        const val MAX_TEXT_LENGTH = 4096
        const val REDACTED_TOKEN = "<bot token>"
    }
}
