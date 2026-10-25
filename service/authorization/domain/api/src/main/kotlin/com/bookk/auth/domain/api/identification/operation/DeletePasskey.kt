package com.bookk.auth.domain.api.identification.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface DeletePasskey {
    suspend operator fun invoke(id: Uuid, authId: Uuid): Result<Unit>

    sealed interface Error {
        class LastPasskey : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.LAST_PASSKEY,
            message = "Can't delete passkey if it's the only one registered."
        ), Error
    }
}