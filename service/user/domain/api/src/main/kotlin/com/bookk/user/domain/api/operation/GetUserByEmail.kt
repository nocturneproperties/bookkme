package com.bookk.user.domain.api.operation

import com.bookk.core.domain.entity.BusinessError
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode

interface GetUserByEmail {
    suspend operator fun invoke(body: EmailBody): Result<User>

    sealed interface Error {
        class UserNotFound : BusinessError(
            statusCode = HttpStatusCode.NotFound.value,
            code = UserErrorCodes.USER_NOT_EXIST,
            message = "User not found"
        ), Error
    }
}