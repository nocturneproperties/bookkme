package com.bookk.user.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import com.bookk.user.domain.api.error.UserErrorCodes
import io.ktor.http.HttpStatusCode

sealed interface LegalDocumentError {
    class VersionUnknown :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = UserErrorCodes.LEGAL_DOCUMENT_VERSION_UNKNOWN,
            message = "Legal document version is not published",
        ),
        LegalDocumentError
}
