package com.bookk.notifications.domain.impl.notification

import com.bookk.core.domain.entity.Language

internal class NotificationParameters(
    val type: NotificationType,
    val push: (Language) -> PushNotification,
    val email: (Language) -> EmailNotification,
    val text: (Language) -> TextNotification
)

internal class PushNotification(
    val title: String,
    val body: String,
)

internal class EmailNotification(
    val subject: String,
    val body: String,
)

internal class TextNotification(
    val text: String
)

internal enum class NotificationType {
    APPOINTMENT,
    EMPLOYEE
}