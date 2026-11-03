package com.bookk.user.domain.api.operation

import com.bookk.core.domain.entity.BusinessError
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode
import library.export.DataExport
import kotlin.uuid.Uuid

interface StartPersonalDataExport {
    suspend operator fun invoke(userId: Uuid, stepUpToken: String): Result<DataExport>

    sealed interface Error {
        class StepUpInvalid :
            BusinessError(
                statusCode = HttpStatusCode.Forbidden.value,
                code = UserErrorCodes.USER_STEP_UP_INVALID,
                message = "Confirm exporting your data with your passkey",
            ),
            Error
    }
}
