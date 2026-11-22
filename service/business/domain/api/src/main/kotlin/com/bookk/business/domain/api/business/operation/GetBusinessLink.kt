package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface GetBusinessLink {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<BusinessLink>

    sealed interface Error {
        class LinkNotFound :
            BusinessError(
                statusCode = HttpStatusCode.NotFound.value,
                code = BusinessErrorCodes.BUSINESS_LINK_NOT_FOUND,
                message = "The business has no link",
            ),
            Error
    }
}
