package com.bookk.notifications.domain.impl.channel.telegram

import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

internal class TelegramBotApiMessengerTest {

    private class SutFixture(
        responses: List<Pair<HttpStatusCode, String>> = listOf(SENT),
        failure: Throwable? = null,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val retryDelays = mutableListOf<Long>()
        private val remaining = ArrayDeque(responses)
        private val engine = MockEngine { request ->
            requests += request
            if (failure != null) throw failure
            answer()
        }
        val sut = TelegramBotApiMessenger(
            httpClient = telegramBotApiHttpClient(engine, retryDelay = { retryDelays += it }),
            botToken = BOT_TOKEN,
        )

        private fun MockRequestHandleScope.answer(): HttpResponseData {
            val (status, body) = remaining.removeFirstOrNull() ?: SENT
            return respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        fun sentJson(index: Int = 0): JsonObject {
            return Json.parseToJsonElement((requests[index].body as TextContent).text).jsonObject
        }
    }

    @Test
    fun `should post the text to the chat through the bot's sendMessage method`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val delivery = fixture.sut.send(123456789, "Your appointment is confirmed")

        then()
        assertEquals(TelegramDelivery.SENT, delivery)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.telegram.org/bot$BOT_TOKEN/sendMessage", request.url.toString())
        assertEquals(123456789, fixture.sentJson()["chat_id"]?.jsonPrimitive?.long)
        assertEquals("Your appointment is confirmed", fixture.sentJson()["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should send plain text without a parse mode`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.send(123456789, "<b>Barber_shop</b> *today*")

        then()
        assertFalse(fixture.sentJson().containsKey("parse_mode"))
        assertEquals("<b>Barber_shop</b> *today*", fixture.sentJson()["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should cut a text longer than Telegram accepts`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.send(123456789, "a".repeat(5000))

        then()
        assertEquals(4096, fixture.sentJson()["text"]?.jsonPrimitive?.content?.length)
    }

    @Test
    fun `should report an unreachable chat when Telegram forbids the message`() = runUnitTest {
        given()
        val fixture = SutFixture(responses = listOf(FORBIDDEN))

        whenn()
        val delivery = fixture.sut.send(123456789, "Text")

        then()
        assertEquals(TelegramDelivery.CHAT_UNREACHABLE, delivery)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `should fail with Telegram's description when the request is refused`() = runUnitTest {
        given()
        val fixture = SutFixture(responses = listOf(BAD_REQUEST))

        whenn()
        val result = runSuspendCatching { fixture.sut.send(123456789, "Text") }

        then()
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("400")) { message }
        assertTrue(message.contains("Bad Request: chat not found")) { message }
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `should retry when Telegram asks to slow down`() = runUnitTest {
        given()
        val fixture = SutFixture(responses = listOf(TOO_MANY_REQUESTS, SENT))

        whenn()
        val delivery = fixture.sut.send(123456789, "Text")

        then()
        assertEquals(TelegramDelivery.SENT, delivery)
        assertEquals(2, fixture.requests.size)
        assertEquals(1, fixture.retryDelays.size)
    }

    @Test
    fun `should retry when Telegram has a server error`() = runUnitTest {
        given()
        val fixture = SutFixture(responses = listOf(BAD_GATEWAY, SENT))

        whenn()
        val delivery = fixture.sut.send(123456789, "Text")

        then()
        assertEquals(TelegramDelivery.SENT, delivery)
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun `should fail after the retries run out`() = runUnitTest {
        given()
        val fixture = SutFixture(responses = List(3) { BAD_GATEWAY })

        whenn()
        val result = runSuspendCatching { fixture.sut.send(123456789, "Text") }

        then()
        assertTrue(result.isFailure)
        assertEquals(3, fixture.requests.size)
    }

    @Test
    fun `should keep the bot token out of a failure's message`() = runUnitTest {
        given()
        val fixture = SutFixture(failure = IOException("Connection reset by api.telegram.org/bot$BOT_TOKEN"))

        whenn()
        val result = runSuspendCatching { fixture.sut.send(123456789, "Text") }

        then()
        val failure = requireNotNull(result.exceptionOrNull())
        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.isNotEmpty())
        assertTrue(messages.none { it.contains(BOT_TOKEN) }) { messages.toString() }
    }

    private companion object {
        const val BOT_TOKEN = "123456:secret-bot-token"
        val SENT = HttpStatusCode.OK to """{"ok":true,"result":{"message_id":1}}"""
        val FORBIDDEN = HttpStatusCode.Forbidden to
            """{"ok":false,"error_code":403,"description":"Forbidden: bot was blocked by the user"}"""
        val BAD_REQUEST = HttpStatusCode.BadRequest to
            """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}"""
        val TOO_MANY_REQUESTS = HttpStatusCode.TooManyRequests to
            """{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 1","parameters":{"retry_after":1}}"""
        val BAD_GATEWAY = HttpStatusCode.BadGateway to """{"ok":false,"error_code":502,"description":"Bad Gateway"}"""
    }
}
