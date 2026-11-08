package com.bookk.auth.domain.api.email.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface ConfirmEmailChange {

    suspend operator fun invoke(userId: Uuid, verificationId: Uuid, code: String): Result<Unit>

    sealed interface Error {
        class EmailAlreadyExist :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.EMAIL_EXIST,
                message = "This email already exists",
            ),
            Error
    }
}
