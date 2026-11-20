package com.bookk.notifications.domain.impl.channel.telegram

internal interface TelegramMessenger {
    suspend fun send(chatId: Long, text: String): TelegramDelivery
}
