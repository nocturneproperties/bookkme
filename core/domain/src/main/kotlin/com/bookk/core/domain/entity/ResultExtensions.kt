package com.bookk.core.domain.entity

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

inline fun <R, reified T> Result<R>.handle(
    onSuccess: (result: R) -> Unit,
    onBusinessError: (error: T) -> Unit,
    onUnexpectedError: (error: Error) -> Unit,
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

suspend inline fun <R> runCatchingUnlessCancelled(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (failure: Throwable) {
    currentCoroutineContext().ensureActive()
    Result.failure(failure)
}

inline fun <R, T : R> Result<T>.recoverSuspendCatching(
    transform: (Throwable) -> R,
): Result<R> = when (val failure = exceptionOrNull()) {
    null -> this
    else -> runSuspendCatching { transform(failure) }
}

suspend inline fun <R> runCleaningUpOnFailure(
    crossinline cleanUp: suspend () -> Unit,
    action: () -> R
): R = try {
    action()
} catch (failure: Throwable) {
    try {
        withContext(NonCancellable) { cleanUp() }
    } catch (cleanUpFailure: Throwable) {
        failure.addSuppressed(cleanUpFailure)
    }
    throw failure
}

fun Iterable<Result<*>>.throwIfAnyFailed() {
    val failures = mapNotNull { it.exceptionOrNull() }
    val first = failures.firstOrNull() ?: return
    failures.drop(1).forEach(first::addSuppressed)
    throw first
}
