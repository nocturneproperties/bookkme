package com.bookk.server.user.client.impl.operation

import com.bookk.core.client.throwOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.entity.TelegramUsernameBody
import com.bookk.user.domain.api.operation.ChangeTelegramUsername
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class ChangeTelegramUsernameClientImpl(
    private val httpClient: HttpClient,
) : ChangeTelegramUsername {

    override suspend fun invoke(userId: Uuid, username: String?): Result<Unit> = runSuspendCatching {
        httpClient.post(UserRouting.Api.Internal.User.ChangeTelegramUsername(id = userId)) {
            setBody(TelegramUsernameBody(username))
        }.throwOnFailure()
    }
}
