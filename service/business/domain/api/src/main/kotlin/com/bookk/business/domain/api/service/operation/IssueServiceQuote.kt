package com.bookk.business.domain.api.service.operation

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.api.service.entity.ServiceQuote
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface IssueServiceQuote {
    suspend operator fun invoke(businessId: Uuid, serviceIds: List<Uuid>): Result<ServiceQuote>

    sealed interface Error {
        class ServiceNotFound : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_QUOTE_SERVICE_NOT_FOUND,
            message = "One or more services not found"
        ), Error

        class EmptyServiceList : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_QUOTE_EMPTY_SERVICE_LIST,
            message = "Service list must not be empty"
        ), Error
    }
}
