package com.bookk.business.domain.api.service.operation

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateServiceGroup {
    suspend operator fun invoke(requestUserId: Uuid, service: ServiceGroup): Result<ServiceGroup>

    sealed interface Error {
        class ServiceGroupExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_GROUP_EXISTS,
            message = "Service group with this name already exists"
        )

        class ValidationError : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_SERVICE_GROUP_VALIDATION_ERROR,
            message = "Invalid service group name"
        )
    }
}