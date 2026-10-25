package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface GetDashboardBusiness {
    suspend operator fun invoke(userId: Uuid): Result<Business>

    sealed interface Error {
        class NotFound : BusinessError(
            statusCode = HttpStatusCode.NotFound.value,
            code = BusinessErrorCodes.BUSINESS_NOT_FOUND,
            message = "No businesses for this user"
        )
    }
}