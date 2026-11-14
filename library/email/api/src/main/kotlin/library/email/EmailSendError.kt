package library.email

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed interface EmailSendError {
    class Undeliverable :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = EmailErrorCodes.EMAIL_UNDELIVERABLE,
            message = "This address can't receive email",
        ),
        EmailSendError
}
