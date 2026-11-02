package com.bookk.user.domain.api.operation

import com.bookk.core.domain.entity.BusinessError
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.entity.UserId
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode

interface CreateUser {

    suspend operator fun invoke(newUser: NewUser): Result<UserId>

    sealed interface Error {
        class LegalDocumentsNotAccepted :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = UserErrorCodes.LEGAL_DOCUMENTS_NOT_ACCEPTED,
                message = "Every legal document in effect must be accepted",
            ),
            Error
    }
}
