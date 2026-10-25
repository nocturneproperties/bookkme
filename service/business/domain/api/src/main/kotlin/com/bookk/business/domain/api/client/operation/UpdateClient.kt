package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface UpdateClient {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, model: ClientUpdateModel): Result<ClientRemote>

    sealed interface Error {
        class NotFound : BusinessError(
            statusCode = HttpStatusCode.NotFound.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_NOT_EXISTS,
            message = "Client not found"
        ), Error

        class ClientExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_EXISTS,
            message = "Client with this phone already exists"
        ), Error

        class ClientValidationError : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR,
            message = "Client name, last name, phone or email is invalid"
        ), Error

        class PersonalInfoNotEditable : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CLIENT_PERSONAL_INFO_NOT_EDITABLE,
            message = "Personal info of an integrated client cannot be edited, only its description can"
        ), Error
    }
}
