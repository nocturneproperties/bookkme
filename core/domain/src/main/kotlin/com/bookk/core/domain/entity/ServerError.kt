package com.bookk.core.domain.entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SimpleServerError(
    @ProtoNumber(1) val errorCode: Int,
    @ProtoNumber(2) val message: String
) {
    override fun toString(): String {
        return "errorCode: $errorCode, message: $message"
    }
}

@Serializable
data class MessageServerError(
    @ProtoNumber(1) val message: String
)

@Serializable
data class BodyServerError<T : Any>(
    @ProtoNumber(1) val message: String,
    @ProtoNumber(2) val errorCode: Int,
    @ProtoNumber(3) val errorBody: T
)

fun BusinessError.asServerError(): SimpleServerError = SimpleServerError(
    message = message,
    errorCode = code
)