package com.bookk.auth.domain.api.demo.operation

import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import io.ktor.http.HttpStatusCode

interface StartDemoUpgrade {
    suspend operator fun invoke(
        caller: DemoCaller,
        name: String,
        email: String,
        language: Language,
    ): Result<SignUpChallengeResponse>

    sealed interface Error {
        class InvalidEmailFormat :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.INVALID_EMAIL_FORMAT,
                message = "Invalid email format",
            ),
            Error

        class EmailDomainUnsupported :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.EMAIL_DOMAIN_UNSUPPORTED,
                message = "Email addresses on this domain are not supported",
            ),
            Error
    }
}
