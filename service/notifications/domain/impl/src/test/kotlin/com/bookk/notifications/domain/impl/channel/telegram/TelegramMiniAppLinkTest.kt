package com.bookk.notifications.domain.impl.channel.telegram

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TelegramMiniAppLinkTest {

    @Test
    fun `should open the bot's main Mini App with the start parameter`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to "bookkme_bot")

        whenn()
        val link = telegramMiniAppLink(environment::get)?.launching("op_delete-account_$CHALLENGE")

        then()
        assertEquals("https://t.me/bookkme_bot?startapp=op_delete-account_$CHALLENGE", link)
    }

    @Test
    fun `should accept the bot username written with an at sign`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to " @bookkme_bot ")

        whenn()
        val link = telegramMiniAppLink(environment::get)?.launching("op_x_1")

        then()
        assertEquals("https://t.me/bookkme_bot?startapp=op_x_1", link)
    }

    @Test
    fun `should build no link when the bot username is not set`() = runUnitTest {
        given()
        val environment = emptyMap<String, String>()

        whenn()
        val link = telegramMiniAppLink(environment::get)

        then()
        assertNull(link)
    }

    @Test
    fun `should build no link when the bot username is blank`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to " ")

        whenn()
        val link = telegramMiniAppLink(environment::get)

        then()
        assertNull(link)
    }

    @Test
    fun `should refuse to start with a bot username Telegram would not accept`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to "bookkme/bot?x=")

        whenn()
        val result = runCatching { telegramMiniAppLink(environment::get) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    private companion object {
        const val CHALLENGE = "0123456789abcdef0123456789abcdef"
    }
}
