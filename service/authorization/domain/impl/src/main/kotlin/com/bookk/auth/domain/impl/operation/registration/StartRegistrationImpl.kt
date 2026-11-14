package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.EmailAlreadyExist
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.InvalidEmailFormat
import com.bookk.auth.domain.impl.email.AuthEmails
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.rethrowBusinessIf
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.UserClient
import io.ktor.http.HttpStatusCode
import library.email.EmailCodeIssuer
import library.validation.EmailValidator
import kotlin.uuid.Uuid

internal class StartRegistrationImpl(
    private val userClient: UserClient,
    private val startPasskeyRegistration: StartPasskeyRegistration,
    private val emailCodeIssuer: EmailCodeIssuer,
) : StartRegistration {

    override suspend fun invoke(
        request: CreateAccountRequest,
        language: Language,
    ): Result<SignUpChallengeResponse> = runSuspendCatching {
        if (!EmailValidator.isValid(request.email)) throw InvalidEmailFormat()
        userClient.getUserByEmail(request.email)
            .onSuccess { throw EmailAlreadyExist() }
            .rethrowBusinessIf { it.statusCode != HttpStatusCode.NotFound.value }
        val passkey = startPasskeyRegistration(
            userHandle = Uuid.random(),
            passkeyDisplayName = "${request.firstName} ${request.lastName}",
        ).getOrThrow()
        val emailVerification = emailCodeIssuer.issue(
            email = request.email,
            purpose = EmailPurpose.SIGN_UP,
            ownerId = Uuid.parse(passkey.requestId),
        ) { code -> AuthEmails.signUpCode(language, code) }.getOrThrow()
        SignUpChallengeResponse.of(passkey, emailVerification)
    }
}
