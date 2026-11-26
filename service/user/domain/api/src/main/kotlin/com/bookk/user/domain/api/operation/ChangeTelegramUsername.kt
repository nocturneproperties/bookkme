package com.bookk.user.domain.api.operation

import com.bookk.core.domain.entity.BusinessError
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface ChangeTelegramUsername {
    suspend operator fun invoke(userId: Uuid, username: String?): Result<Unit>

    sealed interface Error {
        class UserNotFound :
            BusinessError(
                statusCode = HttpStatusCode.NotFound.value,
                code = UserErrorCodes.USER_NOT_EXIST,
                message = "User not found",
            ),
            Error

        class UsernameInvalid :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = UserErrorCodes.TELEGRAM_USERNAME_INVALID,
                message = "Telegram username is invalid",
            ),
            Error
    }
}
