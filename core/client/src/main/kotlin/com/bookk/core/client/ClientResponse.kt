package com.bookk.core.client

import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.domain.entity.runSuspendCatching
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

fun domainErrorOf(
    status: HttpStatusCode,
    serverError: SimpleServerError?,
    cause: Throwable?
): Throwable = when {
    serverError != null -> BusinessError(status.value, serverError.errorCode, serverError.message)
    status == HttpStatusCode.NotFound -> Error.NotFound()
    status == HttpStatusCode.Forbidden -> Error.OperationNotAllowed()
    else -> Error.UnknownError(cause ?: IllegalStateException("Request failed with status $status"))
}

suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    throwOnFailure()
    return body()
}

suspend fun HttpResponse.throwOnFailure() {
    if (status.isSuccess()) return
    val serverError = runSuspendCatching { body<SimpleServerError>() }
    throw domainErrorOf(status, serverError.getOrNull(), serverError.exceptionOrNull())
}
