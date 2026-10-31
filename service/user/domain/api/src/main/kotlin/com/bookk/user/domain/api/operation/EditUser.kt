package com.bookk.user.domain.api.operation

import com.bookk.core.domain.entity.BusinessError
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface EditUser {
    suspend operator fun invoke(id: Uuid, user: UserEditModel): Result<Unit>

    sealed interface Error {
        class UserNotFound : BusinessError(
            statusCode = HttpStatusCode.NotFound.value,
            code = UserErrorCodes.USER_NOT_EXIST,
            message = "User not found"
        ), Error

        class ContactTaken : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = UserErrorCodes.USER_CONTACT_TAKEN,
            message = "Phone or email is already used by another account"
        ), Error
    }
}