package com.bookk.notifications.domain.impl.channel.telegram

internal sealed interface TelegramDelivery {
    data class Sent(val messageId: Long?) : TelegramDelivery

    data object Dropped : TelegramDelivery

    data object ChatUnreachable : TelegramDelivery
}
