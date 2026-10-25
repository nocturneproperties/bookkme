package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface DeleteClient {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit>

    sealed interface Error {
        class NotFound : BusinessError(
            statusCode = HttpStatusCode.NotFound.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS,
            message = "Client not found"
        ), Error
    }
}