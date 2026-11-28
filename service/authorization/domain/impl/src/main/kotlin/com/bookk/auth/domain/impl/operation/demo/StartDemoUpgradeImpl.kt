package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError.EmailAlreadyExist
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade.Error.EmailDomainUnsupported
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade.Error.InvalidEmailFormat
import com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.impl.email.AuthEmails
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.auth.domain.impl.operation.registration.PersonName
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.rethrowBusinessIf
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.UserClient
import io.ktor.http.HttpStatusCode
import library.email.EmailCodeIssuer
import library.email.PlaceholderEmail
import library.validation.EmailValidator
import kotlin.uuid.Uuid

internal class StartDemoUpgradeImpl(
    private val demoCallerResolver: DemoCallerResolver,
    private val userClient: UserClient,
    private val startPasskeyRegistration: StartPasskeyRegistration,
    private val emailCodeIssuer: EmailCodeIssuer,
) : StartDemoUpgrade {

    override suspend fun invoke(
        caller: DemoCaller,
        name: String,
        email: String,
        language: Language,
    ): Result<SignUpChallengeResponse> = runSuspendCatching {
        if (!EmailValidator.isValid(email)) throw InvalidEmailFormat()
        if (PlaceholderEmail.isPlaceholder(email)) throw EmailDomainUnsupported()
        val demo = demoCallerResolver.resolve(caller)
        userClient.getUserByEmail(email)
            .onSuccess { if (it.id != demo.account.userId) throw EmailAlreadyExist() }
            .rethrowBusinessIf { it.statusCode != HttpStatusCode.NotFound.value }
        val passkey = startPasskeyRegistration(
            userHandle = demo.account.uuid,
            passkeyDisplayName = PersonName.of(name).fullName,
        ).getOrThrow()
        val emailVerification = emailCodeIssuer.issue(
            email = email,
            purpose = EmailPurpose.DEMO_UPGRADE,
            ownerId = Uuid.parse(passkey.requestId),
        ) { code -> AuthEmails.signUpCode(language, code) }.getOrThrow()
        SignUpChallengeResponse.of(passkey, emailVerification)
    }
}
