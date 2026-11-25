package com.bookk.core.domain.entity

fun pageRequest(limit: Int = PageRequest.DEFAULT_LIMIT, after: PageCursor? = null): PageRequest {
    return PageRequest.of(limit, after?.encode()).getOrThrow()
}
