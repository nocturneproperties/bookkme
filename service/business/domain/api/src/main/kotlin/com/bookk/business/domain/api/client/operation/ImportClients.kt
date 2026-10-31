package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientImportResult
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface ImportClients {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, clients: List<Client.Detached>): Result<ClientImportResult>

    sealed interface Error {
        class TooManyClients : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_IMPORT_TOO_LARGE,
            message = "A client import can contain at most $MAX_CLIENTS clients"
        ), Error

        class ClientExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_EXISTS,
            message = "Client with this phone or email already exists"
        ), Error
    }

    companion object {
        const val MAX_CLIENTS = 500
    }
}
