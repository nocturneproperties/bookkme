package com.bookk.core.domain.entity

import kotlin.coroutines.cancellation.CancellationException

inline fun <R, reified T> Result<R>.handle(
    onSuccess: (result: R) -> Unit,
    onBusinessError: (error: T) -> Unit,
    onUnexpectedError: (error: Error) -> Unit
) {
    onSuccess {
        onSuccess(it)
    }.onFailure {
        when (it) {
            is T -> onBusinessError(it)
            is Error -> onUnexpectedError(it)
            else -> onUnexpectedError(Error.UnknownError(it))
        }
    }
}

inline fun <R> Result<R>.rethrowIf(condition: (Throwable) -> Boolean) {
    onFailure { if (condition(it)) throw it }
}

inline fun <R> runSuspendCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}

inline fun <R, T : R> Result<T>.recoverSuspendCatching(transform: (Throwable) -> R): Result<R> =
    when (val failure = exceptionOrNull()) {
        null -> this
        else -> runSuspendCatching { transform(failure) }
    }
