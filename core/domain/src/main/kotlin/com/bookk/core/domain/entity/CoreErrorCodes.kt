package com.bookk.core.domain.entity

object CoreErrorCodes {
    private const val BASE = 900000

    const val TRANSACTION_CONFLICT = BASE + 1
    const val PAGE_LIMIT_INVALID = BASE + 3
    const val PAGE_CURSOR_INVALID = BASE + 4
}
