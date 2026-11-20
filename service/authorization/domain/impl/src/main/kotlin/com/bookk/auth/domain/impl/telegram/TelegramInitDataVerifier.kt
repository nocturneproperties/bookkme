package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.telegram.entity.TelegramUser

internal fun interface TelegramInitDataVerifier {
    fun verify(initData: String): TelegramUser
}
