package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessCreateRequest
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateBusiness {
    suspend operator fun invoke(userId: Uuid, request: BusinessCreateRequest): Result<Business>

    sealed interface Error {
        class BusinessExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_ALREADY_EXIST,
            message = "Business already exist"
        )

        class BusinessValidationError : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_NAME_VALIDATION_ERROR,
            message = "Business name invalid"
        )

        class InvalidCurrency : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_INVALID_CURRENCY,
            message = "Currency code is not a supported ISO 4217 currency"
        ), Error
    }
}