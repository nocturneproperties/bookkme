package com.bookk.user.domain.api.entity

import kotlin.uuid.Uuid


data class ContactForm(
    val userId: Uuid,
    val text: String,
    val usageLogs: String?,
    val status: ContactFormStatus
) {

    val isBoundCapRequired: Boolean
        get() = isTextOutOfBounds || isLogsOutOfBounds

    private val isTextOutOfBounds: Boolean
        get() = text.length > UShort.MAX_VALUE.toInt()

    private val isLogsOutOfBounds: Boolean
        get() = usageLogs.orEmpty().length > UShort.MAX_VALUE.toInt()

    enum class ContactFormStatus(val id: Byte) {
        NEW(0),
        PROCESSING(1),
        BLOCKED(2),
        DONE(30)
    }

    companion object {
        const val TEXT_UPPER_BOUND = UShort.MAX_VALUE
        const val LOGS_UPPER_BOUND = UShort.MAX_VALUE
    }
}