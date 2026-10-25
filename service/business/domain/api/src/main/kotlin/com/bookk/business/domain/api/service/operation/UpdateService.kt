package com.bookk.business.domain.api.service.operation

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface UpdateService {
    suspend operator fun invoke(requestUserId: Uuid, service: Service): Result<Service>

    sealed interface Error {
        class ServiceExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_EXISTS,
            message = "Service with this name already exists"
        )

        class ValidationError : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_NAME_VALIDATION_ERROR,
            message = "Invalid service name"
        )

        class PricePrecisionInvalid : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_PRICE_PRECISION_INVALID,
            message = "Service price has more decimal places than the business currency allows"
        ), Error

        class BusinessCurrencyInvalid : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_BUSINESS_CURRENCY_INVALID,
            message = "Business currency is not a supported currency, update the business currency first"
        ), Error

        class ServiceGroupNotFound : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_GROUP_NOT_EXISTS,
            message = "Service group does not exist in this business"
        ), Error
    }
}