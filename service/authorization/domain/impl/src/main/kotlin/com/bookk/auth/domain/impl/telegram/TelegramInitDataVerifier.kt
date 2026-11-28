package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.telegram.entity.TelegramUser
import kotlin.time.Duration

internal interface TelegramInitDataVerifier {
    val isConfigured: Boolean

    fun verify(initData: String): TelegramUser
    fun verifyLaunch(initData: String, maxAge: Duration): TelegramLaunch
}
