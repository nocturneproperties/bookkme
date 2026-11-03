package com.bookk.business.domain.api.export

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import library.export.DataExport
import kotlin.uuid.Uuid

interface StartBusinessDataExport {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid, stepUpToken: String): Result<DataExport>

    sealed interface Error {
        class StepUpInvalid :
            BusinessError(
                statusCode = HttpStatusCode.Forbidden.value,
                code = BusinessErrorCodes.BUSINESS_STEP_UP_INVALID,
                message = "Confirm exporting the business's data with your passkey",
            ),
            Error
    }
}
