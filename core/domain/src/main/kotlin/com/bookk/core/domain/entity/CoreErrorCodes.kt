package com.bookk.core.domain.entity

object CoreErrorCodes {
    private const val BASE = 900000

    const val TRANSACTION_CONFLICT = BASE + 1
    const val DATA_EXPORT_TOO_FREQUENT = BASE + 2
}
