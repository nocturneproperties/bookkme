package com.bookk.server.user.client.impl.operation

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserId
import com.bookk.user.domain.api.operation.CreateUser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody

internal class CreateUserClientImpl(
    private val httpClient: HttpClient
) : CreateUser {

    override suspend fun invoke(user: User) = runSuspendCatching {
        httpClient.post(UserRouting.Api.Internal.User()) {
            setBody(user)
        }.bodyOrThrow<UserId>()
    }
}