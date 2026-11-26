package com.bookk.notifications.domain.impl.channel.telegram

internal const val TELEGRAM_BOT_USERNAME = "APPLICATION_TELEGRAM_BOT_USERNAME"

private val BOT_USERNAME_PATTERN = Regex("^[A-Za-z0-9_]{5,32}$")

internal fun telegramBotUsername(environment: (String) -> String?): String? {
    val botUsername = environment(TELEGRAM_BOT_USERNAME)?.trim()?.removePrefix("@")
    if (botUsername.isNullOrEmpty()) return null
    check(BOT_USERNAME_PATTERN.matches(botUsername)) { "$TELEGRAM_BOT_USERNAME is not a Telegram bot username" }
    return botUsername
}
