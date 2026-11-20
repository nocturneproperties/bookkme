package com.bookk.notifications.domain.impl.channel.telegram

import io.ktor.client.engine.HttpClientEngine

internal const val TELEGRAM_BOT_TOKEN = "APPLICATION_TELEGRAM_BOT_TOKEN"

internal fun telegramMessenger(environment: (String) -> String?, engine: HttpClientEngine): TelegramMessenger {
    val botToken = environment(TELEGRAM_BOT_TOKEN)?.trim()
    if (botToken.isNullOrEmpty()) return DroppingTelegramMessenger()
    return TelegramBotApiMessenger(telegramBotApiHttpClient(engine), botToken)
}
