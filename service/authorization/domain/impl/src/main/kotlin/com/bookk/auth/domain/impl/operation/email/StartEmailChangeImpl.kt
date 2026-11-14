package com.bookk.auth.domain.impl.operation.email

import com.bookk.auth.domain.api.email.operation.StartEmailChange
import com.bookk.auth.domain.api.email.operation.StartEmailChange.Error.EmailAlreadyExist
import com.bookk.auth.domain.api.email.operation.StartEmailChange.Error.InvalidEmailFormat
import com.bookk.auth.domain.impl.email.AuthEmails
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.rethrowBusinessIf
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.UserClient
import io.ktor.http.HttpStatusCode
import library.email.EmailCodeIssuer
import library.email.EmailVerificationStarted
import library.validation.EmailValidator
import kotlin.uuid.Uuid

internal class StartEmailChangeImpl(
    private val userClient: UserClient,
    private val emailCodeIssuer: EmailCodeIssuer,
) : StartEmailChange {

    override suspend fun invoke(
        userId: Uuid,
        email: String,
        language: Language,
    ): Result<EmailVerificationStarted> = runSuspendCatching {
        if (!EmailValidator.isValid(email)) throw InvalidEmailFormat()
        userClient.getUserByEmail(email)
            .onSuccess { throw EmailAlreadyExist() }
            .rethrowBusinessIf { it.statusCode != HttpStatusCode.NotFound.value }
        emailCodeIssuer.issue(email, EmailPurpose.EMAIL_CHANGE, ownerId = userId) { code ->
            AuthEmails.emailChangeCode(language, code)
        }.getOrThrow()
    }
}
