package com.bookk.auth.domain.api.delete_account.operation

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface DeleteAccount {

    suspend operator fun invoke(userId: Uuid, request: FinishAssertionRequest): Result<Unit>

    sealed interface Error {
        class InvalidCredentials : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.VERIFICATION_FAILED,
            message = "Invalid credentials"
        ), Error
    }
}