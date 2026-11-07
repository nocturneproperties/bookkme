package com.bookk.user.domain.api.export

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ContactMessageExport(
    val text: String,
    val usageLogs: String?,
    val status: String,
    val sentAt: Instant,
) {
    companion object {
        fun stub(
            text: String = "The calendar doesn't load",
            usageLogs: String? = null,
            status: String = "NEW",
            sentAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = ContactMessageExport(text, usageLogs, status, sentAt)
    }
}
