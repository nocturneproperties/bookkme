package com.bookk.notifications.domain.impl.notification.renderer

import com.bookk.core.domain.entity.Language
import com.bookk.core.i18n.toLocale
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

private val FORMATTERS: Map<Language, DateTimeFormatter> = mapOf(
    Language.EN to DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Language.EN.toLocale()),
    Language.UK to DateTimeFormatter.ofPattern("d MMMM yyyy 'р.', HH:mm", Language.UK.toLocale()),
)

internal fun Instant.formatLocalized(timeZone: TimeZone, language: Language): String {
    val javaDateTime = toLocalDateTime(timeZone).toJavaLocalDateTime()
    return FORMATTERS.getValue(language).format(javaDateTime)
}
