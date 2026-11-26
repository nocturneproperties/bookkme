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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class TelegramBotApiMessenger(
    private val httpClient: HttpClient,
    private val botToken: String,
) : TelegramMessenger {

    override suspend fun send(chatId: Long, text: String, button: TelegramUrlButton?): TelegramDelivery {
        val request = SendMessageRequest(
            chatId = chatId,
            text = text.take(MAX_TEXT_LENGTH),
            replyMarkup = button?.let(::keyboardOf),
        )
        val response = call("sendMessage", request)
        if (response.status.isSuccess()) return TelegramDelivery.Sent(response.messageId())
        if (response.status == HttpStatusCode.Forbidden) return TelegramDelivery.ChatUnreachable
        throw response.refusal("message")
    }

    override suspend fun edit(chatId: Long, messageId: Long, text: String): Boolean {
        val request = EditMessageTextRequest(
            chatId = chatId,
            messageId = messageId,
            text = text.take(MAX_TEXT_LENGTH),
            replyMarkup = InlineKeyboardMarkup(emptyList()),
        )
        return isApplied(call("editMessageText", request), "edit")
    }

    override suspend fun delete(chatId: Long, messageId: Long): Boolean {
        return isApplied(call("deleteMessage", DeleteMessageRequest(chatId, messageId)), "deletion")
    }

    private suspend fun isApplied(response: HttpResponse, change: String): Boolean {
        if (response.status.isSuccess()) return true
        if (response.status in MESSAGE_UNCHANGEABLE) {
            logger.info(
                "Telegram refused the {} with HTTP {}: {}",
                change,
                response.status.value,
                response.description(),
            )
            return false
        }
        throw response.refusal(change)
    }

    private suspend inline fun <reified T> call(method: String, request: T): HttpResponse {
        return runSuspendCatching {
            httpClient.post("$TELEGRAM_BOT_API_URL/bot$botToken/$method") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.getOrElse { failure ->
            val reason = failure.message?.replace(botToken, REDACTED_TOKEN)
            throw IllegalStateException("Telegram Bot API request failed with ${failure::class.simpleName}: $reason")
        }
    }

    private suspend fun HttpResponse.refusal(subject: String): IllegalStateException {
        return IllegalStateException(
            "Telegram Bot API refused the $subject with HTTP ${status.value}: ${description()}",
        )
    }

    private suspend fun HttpResponse.messageId(): Long? {
        return runSuspendCatching { body<SentMessageResponse>().result?.messageId }.getOrNull()
    }

    private fun keyboardOf(button: TelegramUrlButton): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton(text = button.text, url = button.url))))
    }

    private suspend fun HttpResponse.description(): String? {
        return runSuspendCatching { body<TelegramBotApiRefusal>().description }.getOrNull()
    }

    @Serializable
    private class SendMessageRequest(
        @SerialName("chat_id") val chatId: Long,
        @SerialName("text") val text: String,
        @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null,
    )

    @Serializable
    private class EditMessageTextRequest(
        @SerialName("chat_id") val chatId: Long,
        @SerialName("message_id") val messageId: Long,
        @SerialName("text") val text: String,
        @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup,
    )

    @Serializable
    private class DeleteMessageRequest(
        @SerialName("chat_id") val chatId: Long,
        @SerialName("message_id") val messageId: Long,
    )

    @Serializable
    private class SentMessageResponse(
        @SerialName("result") val result: SentMessage? = null,
    )

    @Serializable
    private class SentMessage(
        @SerialName("message_id") val messageId: Long,
    )

    @Serializable
    private class InlineKeyboardMarkup(
        @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>,
    )

    @Serializable
    private class InlineKeyboardButton(
        @SerialName("text") val text: String,
        @SerialName("url") val url: String,
    )

    @Serializable
    private class TelegramBotApiRefusal(
        @SerialName("description") val description: String? = null,
    )

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(TelegramBotApiMessenger::class.java)
        const val MAX_TEXT_LENGTH = 4096
        const val REDACTED_TOKEN = "<bot token>"
        val MESSAGE_UNCHANGEABLE = setOf(HttpStatusCode.BadRequest, HttpStatusCode.Forbidden)
    }
}
