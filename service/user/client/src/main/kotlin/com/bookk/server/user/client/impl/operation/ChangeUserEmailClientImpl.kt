package com.bookk.server.user.client.impl.operation

import com.bookk.core.client.throwOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.operation.ChangeUserEmail
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class ChangeUserEmailClientImpl(
    private val httpClient: HttpClient,
) : ChangeUserEmail {

    override suspend fun invoke(userId: Uuid, verifiedEmail: String): Result<Unit> = runSuspendCatching {
        httpClient.post(UserRouting.Api.Internal.User.ChangeEmail(id = userId)) {
            setBody(EmailBody(verifiedEmail))
        }.throwOnFailure()
    }
}
