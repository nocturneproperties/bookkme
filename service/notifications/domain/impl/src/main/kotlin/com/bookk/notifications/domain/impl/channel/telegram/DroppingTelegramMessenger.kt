package com.bookk.notifications.domain.impl.channel.telegram

import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class DroppingTelegramMessenger : TelegramMessenger {

    init {
        logger.warn("$TELEGRAM_BOT_TOKEN is not set, so no notification is sent through Telegram")
    }

    override suspend fun send(chatId: Long, text: String, button: TelegramUrlButton?): TelegramDelivery {
        return TelegramDelivery.Dropped
    }

    override suspend fun edit(chatId: Long, messageId: Long, text: String): Boolean {
        return false
    }

    override suspend fun delete(chatId: Long, messageId: Long): Boolean {
        return false
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(DroppingTelegramMessenger::class.java)
    }
}
