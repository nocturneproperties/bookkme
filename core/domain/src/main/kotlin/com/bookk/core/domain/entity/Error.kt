package com.bookk.core.domain.entity

sealed class Error(cause: Throwable) : Throwable(cause) {
    class OperationNotAllowed : Error(IllegalAccessException())
    class NotFound : Error(IllegalStateException())
    class DatabaseError(val text: String, cause: Throwable) : Error(cause)
    class UniqueConstraintFailed(val text: String, cause: Throwable) : Error(cause)
    class TransactionConflict(val text: String, cause: Throwable) : Error(cause)
    class UnknownError(cause: Throwable) : Error(cause)
}

fun Throwable.isConstraintFailure() = this is Error.UniqueConstraintFailed
fun Throwable.isPermissionFailure() = this is Error.OperationNotAllowed
fun Throwable.isBusinessFailure() = this is BusinessError

suspend inline fun <T> Result<T>.onConstraintFailure(crossinline action: suspend () -> Nothing): Result<T> {
    return recoverSuspendCatching {
        if (it.isConstraintFailure()) action()
        else throw it
    }
}

suspend inline fun <T> Result<T>.onPermissionsMissing(crossinline action: suspend () -> Nothing): Result<T> {
    return recoverSuspendCatching {
        if (it.isPermissionFailure()) action()
        else throw it
    }
}

suspend inline fun <T> Result<T>.onBusinessFailure(crossinline action: suspend (BusinessError) -> Nothing): Result<T> {
    return recoverSuspendCatching {
        if (it.isBusinessFailure()) action(it as BusinessError)
        else throw it
    }
}

suspend inline fun <T> Result<T>.onPermissionsMissingReturn(crossinline action: suspend () -> T): Result<T> {
    return recoverSuspendCatching {
        if (it.isPermissionFailure()) action()
        else throw it
    }
}

suspend inline fun <R> Result<R>.rethrowBusinessIf(crossinline condition: suspend (BusinessError) -> Boolean) {
    onFailure { if (it.isBusinessFailure() && condition(it as BusinessError)) throw it }
}