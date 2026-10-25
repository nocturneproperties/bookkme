package com.bookk.auth.domain.api.registration.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language

interface FinishRegistration {

    suspend operator fun invoke(request: VerifyAccountCreationRequest, language: Language): Result<AuthTokens>

    sealed interface Error {
        class InvalidEmailFormat : BusinessError(422, AuthErrorCodes.INVALID_EMAIL_FORMAT, "Invalid email format"), Error
        class UserAlreadyExist : BusinessError(422, AuthErrorCodes.USER_ALREADY_EXIST, "User with this email already exist"), Error
        class AccountCreationFailed : BusinessError(422, AuthErrorCodes.ACCOUNT_CREATION_FAILED, "Error during account creation, try again later"), Error
    }
}