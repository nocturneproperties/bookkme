package com.bookk.auth.domain.api.identification.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface GetAttachPasskeyToAccountChallenge {
    suspend operator fun invoke(authId: Uuid, deviceId: Uuid, userId: Uuid): Result<RegistrationChallengeResponse>

    sealed interface Error {
        class UnableToGeneratePasskeyChallenge :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.UNABLE_TO_GENERATE_REGISTRATION_CHALLENGE,
                message = "Unable to provide registration challenge",
            ),
            Error

        class PasskeysLimitReached :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.PASSKEYS_LIMIT_REACHED,
                message = "Account already has the maximum number of passkeys",
            ),
            Error
    }
}
