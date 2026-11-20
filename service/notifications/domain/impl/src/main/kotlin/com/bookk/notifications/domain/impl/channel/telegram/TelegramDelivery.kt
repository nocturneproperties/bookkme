package com.bookk.notifications.domain.impl.channel.telegram

internal enum class TelegramDelivery {
    SENT,
    DROPPED,
    CHAT_UNREACHABLE,
}
