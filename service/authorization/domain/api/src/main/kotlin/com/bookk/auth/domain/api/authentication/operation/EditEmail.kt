package com.bookk.auth.domain.api.authentication.operation

import com.bookk.auth.domain.api.authentication.entity.EditEmailRequest
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

interface EditEmail {
    suspend operator fun invoke(authId: Long, request: EditEmailRequest): Result<Unit>

    sealed interface Error {
        class UserNotFound : BusinessError(HttpStatusCode.NotFound.value, AuthErrorCodes.PASSKEY_OWNER_NOT_FOUND, "User for this auth token not found")
        class EmailTaken : BusinessError(HttpStatusCode.UnprocessableEntity.value, AuthErrorCodes.EMAIL_EXIST, "User with this email already exist")
    }
}