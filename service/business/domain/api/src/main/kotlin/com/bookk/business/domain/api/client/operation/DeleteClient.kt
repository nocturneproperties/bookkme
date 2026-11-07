package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface DeleteClient {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid, confirmationToken: String): Result<Unit>

    sealed interface Error {
        class NotFound :
            BusinessError(
                statusCode = HttpStatusCode.NotFound.value,
                code = BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS,
                message = "Client not found",
            ),
            Error

        class ActionConfirmationInvalid :
            BusinessError(
                statusCode = HttpStatusCode.Forbidden.value,
                code = BusinessErrorCodes.BUSINESS_ACTION_CONFIRMATION_INVALID,
                message = "Confirm deleting the client with your passkey",
            ),
            Error
    }
}
