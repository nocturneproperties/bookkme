package com.bookk.core.domain.entity

class PageRequest private constructor(
    val limit: Int,
    val after: PageCursor?,
) {
    val fetchSize: Int
        get() = limit + 1

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 100
        const val MAX_CURSOR_LENGTH = 256

        fun of(limit: Int?, cursor: String?): Result<PageRequest> {
            val resolvedLimit = limit ?: DEFAULT_LIMIT
            if (resolvedLimit !in 1..MAX_LIMIT) return Result.failure(PageError.LimitInvalid())
            if (cursor.isNullOrEmpty()) return Result.success(PageRequest(resolvedLimit, after = null))
            val after = cursor.takeIf { it.length <= MAX_CURSOR_LENGTH }?.let(PageCursor::decodeOrNull)
                ?: return Result.failure(PageError.CursorInvalid())
            return Result.success(PageRequest(resolvedLimit, after))
        }
    }
}
