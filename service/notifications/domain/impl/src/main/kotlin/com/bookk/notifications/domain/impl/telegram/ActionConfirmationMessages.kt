package com.bookk.notifications.domain.impl.telegram

import com.bookk.core.domain.entity.Language
import com.bookk.notifications.client.api.telegram.TelegramConfirmedAction
import com.bookk.notifications.domain.impl.notification.renderer.message

internal fun actionConfirmationText(action: TelegramConfirmedAction, language: Language): String {
    val key = when (action) {
        TelegramConfirmedAction.DELETE_ACCOUNT -> "telegram.action_confirmation.delete_account.text"
        TelegramConfirmedAction.EXPORT_PERSONAL_DATA -> "telegram.action_confirmation.export_personal_data.text"
    }
    return message(language, key)
}

internal fun actionConfirmationButton(language: Language): String {
    return message(language, "telegram.action_confirmation.button")
}

internal fun actionConfirmedText(language: Language): String {
    return message(language, "telegram.action_confirmation.confirmed")
}
