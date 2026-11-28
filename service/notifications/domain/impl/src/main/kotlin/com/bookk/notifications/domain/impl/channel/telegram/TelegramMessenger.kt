package com.bookk.notifications.domain.impl.channel.telegram

internal interface TelegramMessenger {
    suspend fun send(chatId: Long, text: String, button: TelegramUrlButton? = null): TelegramDelivery

    suspend fun edit(chatId: Long, messageId: Long, text: String): Boolean

    suspend fun delete(chatId: Long, messageId: Long): Boolean
}
