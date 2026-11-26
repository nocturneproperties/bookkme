package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.telegram.entity.TelegramUser

internal data class TelegramLaunch(
    val user: TelegramUser,
    val startParam: String?,
)
