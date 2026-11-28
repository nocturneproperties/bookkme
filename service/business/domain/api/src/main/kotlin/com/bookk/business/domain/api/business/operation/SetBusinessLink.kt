package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface SetBusinessLink {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid, name: String, isDemo: Boolean): Result<BusinessLink>

    sealed interface Error {
        class LinkInvalid :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = BusinessErrorCodes.BUSINESS_LINK_INVALID,
                message = "Link name must be 3 to 32 latin letters, digits or single hyphens between them",
            ),
            Error

        class LinkReserved :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = BusinessErrorCodes.BUSINESS_LINK_RESERVED,
                message = "This link name is reserved",
            ),
            Error

        class LinkTaken :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = BusinessErrorCodes.BUSINESS_LINK_TAKEN,
                message = "This link name is already taken",
            ),
            Error

        class ChangeLimitReached :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = BusinessErrorCodes.BUSINESS_LINK_CHANGE_LIMIT_REACHED,
                message = "The link was changed too often, try again later",
            ),
            Error

        class DemoRestricted :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = BusinessErrorCodes.BUSINESS_DEMO_RESTRICTED,
                message = "A demo account can't choose a link name, upgrade the account first",
            ),
            Error
    }
}
