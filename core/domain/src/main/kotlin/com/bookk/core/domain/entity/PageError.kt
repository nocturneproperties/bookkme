package com.bookk.core.domain.entity

private const val UNPROCESSABLE_ENTITY = 422

sealed interface PageError {
    class LimitInvalid :
        BusinessError(
            statusCode = UNPROCESSABLE_ENTITY,
            code = CoreErrorCodes.PAGE_LIMIT_INVALID,
            message = "Page limit must be from 1 to ${PageRequest.MAX_LIMIT}",
        ),
        PageError

    class CursorInvalid :
        BusinessError(
            statusCode = UNPROCESSABLE_ENTITY,
            code = CoreErrorCodes.PAGE_CURSOR_INVALID,
            message = "Page cursor is invalid, start from the first page",
        ),
        PageError
}
