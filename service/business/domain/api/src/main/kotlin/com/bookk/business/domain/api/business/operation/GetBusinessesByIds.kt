package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface GetBusinessesByIds {
    suspend operator fun invoke(ids: List<Uuid>, requestingUserId: Uuid): Result<List<Business>>

    sealed interface Error {
        class TooManyIds :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = BusinessErrorCodes.BUSINESS_BULK_READ_TOO_LARGE,
                message = "At most $MAX_IDS businesses can be read at once",
            ),
            Error
    }

    companion object {
        const val MAX_IDS = 100
    }
}
