package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.core.domain.entity.Language
import com.bookk.notifications.domain.impl.notification.renderer.message

internal fun String.orMissingReason(language: Language): String {
    return ifBlank { message(language, "appointment.reason.missing") }
}
