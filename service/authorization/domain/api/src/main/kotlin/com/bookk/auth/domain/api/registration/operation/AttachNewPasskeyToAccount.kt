package com.bookk.auth.domain.api.registration.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface AttachNewPasskeyToAccount {
    suspend operator fun invoke(authId: Uuid, request: AddPasskeyRequest): Result<Unit>

    sealed interface Error {
        class PasskeysLimitReached :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.PASSKEYS_LIMIT_REACHED,
                message = "Account already has the maximum number of passkeys",
            ),
            Error
    }

    companion object {
        const val MAX_PASSKEYS = 10
    }
}
