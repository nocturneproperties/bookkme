package com.bookk.notifications.domain.impl.notification.renderer

import com.bookk.core.domain.entity.Language
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import library.email.EmailContent

internal fun NotificationParameters.emailContent(language: Language): EmailContent {
    val notification = email(language)
    val footer = message(language, type.footerKey)
    return EmailContent(
        subject = notification.subject,
        textBody = "${notification.body}\n\n$footer",
        htmlBody = "<p>${notification.body.escapedHtml()}</p>" +
            "<p style=\"color:#6b7280;font-size:12px\">${footer.escapedHtml()}</p>",
        tag = "notification-${type.name.lowercase()}",
    )
}

private val NotificationType.footerKey: String
    get() = when (this) {
        NotificationType.ACCOUNT -> "email.footer.account"
        NotificationType.APPOINTMENT, NotificationType.EMPLOYEE -> "email.footer.settings"
    }

private fun String.escapedHtml(): String = buildString(length) {
    this@escapedHtml.forEach { character ->
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(character)
        }
    }
}
