package com.bookk.notifications.domain.impl.notification.renderer

import com.bookk.core.domain.entity.Language
import com.bookk.core.i18n.LocalizedStrings

private val messages = LocalizedStrings("i18n.notifications")

internal fun message(language: Language, key: String, vararg args: Any): String =
    messages.message(language, key, *args)

internal fun pluralMessage(language: Language, key: String, count: Int, vararg args: Pair<String, Any>): String =
    messages.pluralMessage(language, key, count, *args)
