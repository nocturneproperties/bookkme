package com.bookk.server.user.client.impl.operation

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.GetUserById
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class GetUserByIdClientImpl(
    private val httpClient: HttpClient
) : GetUserById {

    override suspend fun invoke(userId: Uuid): Result<User> = runSuspendCatching {
        httpClient.get(UserRouting.Api.Internal.User.Id(id = userId)).bodyOrThrow()
    }

}