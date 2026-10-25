package com.bookk.server.user.client.impl.operation

import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserByEmail.Error
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

internal class GetUserByEmailClientImpl(
    private val httpClient: HttpClient
) : GetUserByEmail {
    override suspend fun invoke(body: EmailBody): Result<User> = runSuspendCatching {
        val response = httpClient.get(UserRouting.Api.Internal.User.Email()) {
            setBody(body)
        }
        if (response.status.isSuccess()) {
            response.body<User>()
        } else {
            when (response.body<SimpleServerError>().errorCode) {
                UserErrorCodes.USER_NOT_EXIST -> throw Error.UserNotFound()
                else -> throw UnsupportedOperationException()
            }
        }
    }
}