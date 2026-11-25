package com.bookk.core.domain.entity

class Page<T>(val items: List<T>, val next: PageCursor?) {
    val nextCursor: String?
        get() = next?.encode()

    fun <R> map(transform: (T) -> R): Page<R> {
        return Page(items.map(transform), next)
    }
}

fun <T> List<T>.toPage(request: PageRequest, cursorOf: (T) -> PageCursor): Page<T> {
    if (size <= request.limit) return Page(this, next = null)
    val items = take(request.limit)
    return Page(items, next = cursorOf(items.last()))
}
