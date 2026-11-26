package com.bookk.notifications.domain.impl.channel.telegram

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TelegramBotUsernameTest {

    @Test
    fun `should read the bot username`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to "bookkme_bot")

        whenn()
        val username = telegramBotUsername(environment::get)

        then()
        assertEquals("bookkme_bot", username)
    }

    @Test
    fun `should accept the bot username written with an at sign`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to " @bookkme_bot ")

        whenn()
        val username = telegramBotUsername(environment::get)

        then()
        assertEquals("bookkme_bot", username)
    }

    @Test
    fun `should have no bot username when it is not set`() = runUnitTest {
        given()
        val environment = emptyMap<String, String>()

        whenn()
        val username = telegramBotUsername(environment::get)

        then()
        assertNull(username)
    }

    @Test
    fun `should have no bot username when it is blank`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to " ")

        whenn()
        val username = telegramBotUsername(environment::get)

        then()
        assertNull(username)
    }

    @Test
    fun `should refuse to start with a bot username Telegram would not accept`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to "bookkme/bot?x=")

        whenn()
        val result = runCatching { telegramBotUsername(environment::get) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
