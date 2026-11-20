package com.bookk.notifications.domain.impl.channel.telegram

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TelegramMessengerFactoryTest {

    @Test
    fun `should call the Bot API when a bot token is set`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_TOKEN" to "123456:secret-bot-token")

        whenn()
        val messenger = telegramMessenger(environment::get, MockEngine { respondOk() })

        then()
        assertTrue(messenger is TelegramBotApiMessenger)
    }

    @Test
    fun `should drop every message when no bot token is set`() = runUnitTest {
        given()
        val environment = emptyMap<String, String>()

        whenn()
        val messenger = telegramMessenger(environment::get, MockEngine { respondOk() })

        then()
        assertEquals(TelegramDelivery.DROPPED, messenger.send(123456789, "Text"))
    }

    @Test
    fun `should drop every message when the bot token is blank`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_TOKEN" to " ")

        whenn()
        val messenger = telegramMessenger(environment::get, MockEngine { respondOk() })

        then()
        assertEquals(TelegramDelivery.DROPPED, messenger.send(123456789, "Text"))
    }
}
