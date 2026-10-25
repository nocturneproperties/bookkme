package com.bookk.core.service.enity

import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.CoreErrorCodes
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.logError
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall

suspend inline fun <reified R : Any> RoutingCall.respondWith(result: Result<R>) {
    result
        .onSuccess {
            if (it is Unit) {
                respond(HttpStatusCode.NoContent)
            } else {
                respond(status = HttpStatusCode.OK, message = it)
            }
        }
        .onFailure { error ->
            when (error) {
                is BusinessError -> {
                    respond(
                        status = HttpStatusCode.fromValue(error.statusCode),
                        message = SimpleServerError(error.code, error.message)
                    )
                }

                is Error.TransactionConflict -> {
                    respond(
                        status = HttpStatusCode.Conflict,
                        message = SimpleServerError(
                            CoreErrorCodes.TRANSACTION_CONFLICT,
                            "The request conflicted with concurrent changes, retry it"
                        )
                    )
                }

                is Error.OperationNotAllowed,
                is Error.NotFound -> {
                    respond(
                        status = HttpStatusCode.NotFound,
                        message = "Not found"
                    )
                }

                else -> {
                    logError(call = this, error)
                    respond(HttpStatusCode.InternalServerError)
                }
            }
        }
}