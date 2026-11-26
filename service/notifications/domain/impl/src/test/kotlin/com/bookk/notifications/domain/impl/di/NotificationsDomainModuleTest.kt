package com.bookk.notifications.domain.impl.di

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NotificationsDomainModuleTest {

    @Test
    fun `should refuse to start with a bot username Telegram would not accept`() = runUnitTest {
        given()
        val environment = mapOf("APPLICATION_TELEGRAM_BOT_USERNAME" to "my-bot")

        whenn()
        val result = runCatching { notificationsDomainModule(environment::get) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException) { result.toString() }
    }

    @Test
    fun `should start without any Telegram setting`() = runUnitTest {
        given()
        val environment = emptyMap<String, String>()

        whenn()
        val result = runCatching { notificationsDomainModule(environment::get) }

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }
}
