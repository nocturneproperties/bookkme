package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateClient {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, client: Client): Result<ClientRemote>

    sealed interface Error {
        class ClientExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_EXISTS,
            message = "Client with this phone already exists"
        )

        class ClientValidationError : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR,
            message = "Client name, last name, phone or email is invalid"
        )

        class MissingContactInfo : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_MISSING_CONTACT_INFO,
            message = "Client must have at least a phone or an email"
        )
    }
}