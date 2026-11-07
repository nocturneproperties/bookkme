package library.email

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed interface EmailCodeError {
    class Invalid :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = EmailErrorCodes.EMAIL_CODE_INVALID,
            message = "The code is wrong",
        ),
        EmailCodeError

    class Expired :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = EmailErrorCodes.EMAIL_CODE_EXPIRED,
            message = "The code has expired or was already used, request a new one",
        ),
        EmailCodeError

    class AttemptsExceeded :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = EmailErrorCodes.EMAIL_CODE_ATTEMPTS_EXCEEDED,
            message = "Too many wrong codes, request a new one",
        ),
        EmailCodeError

    class ResendTooSoon :
        BusinessError(
            statusCode = HttpStatusCode.TooManyRequests.value,
            code = EmailErrorCodes.EMAIL_CODE_RESEND_TOO_SOON,
            message = "A code was sent to this address too recently",
        ),
        EmailCodeError
}
