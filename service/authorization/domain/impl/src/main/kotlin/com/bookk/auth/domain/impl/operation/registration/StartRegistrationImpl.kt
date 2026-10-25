package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.EmailAlreadyExist
import com.bookk.auth.domain.api.registration.operation.StartRegistration.Error.InvalidEmailFormat
import com.bookk.core.domain.entity.rethrowBusinessIf
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.UserClient
import io.ktor.http.HttpStatusCode
import library.validation.EmailValidator
import kotlin.uuid.Uuid

internal class StartRegistrationImpl(
    private val userClient: UserClient,
    private val startPasskeyRegistration: StartPasskeyRegistration,
) : StartRegistration {

    override suspend fun invoke(request: CreateAccountRequest) = runSuspendCatching {
        if (!EmailValidator.isValid(request.email)) throw InvalidEmailFormat()
        userClient.getUserByEmail(request.email)
            .onSuccess { throw EmailAlreadyExist() }
            .rethrowBusinessIf { it.statusCode != HttpStatusCode.NotFound.value }
        startPasskeyRegistration(
            userHandle = Uuid.random(),
            passkeyDisplayName = "${request.firstName} ${request.lastName}"
        ).getOrThrow()
    }
}