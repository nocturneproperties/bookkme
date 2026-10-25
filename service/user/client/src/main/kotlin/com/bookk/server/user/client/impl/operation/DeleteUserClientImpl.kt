package com.bookk.server.user.client.impl.operation

import com.bookk.core.client.throwOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.operation.DeleteUser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class DeleteUserClientImpl(
    private val httpClient: HttpClient
) : DeleteUser {

    override suspend fun invoke(userId: Uuid): Result<Unit> = runSuspendCatching {
        httpClient.get(UserRouting.Api.Internal.User.Id(id = userId)).throwOnFailure()
    }

}