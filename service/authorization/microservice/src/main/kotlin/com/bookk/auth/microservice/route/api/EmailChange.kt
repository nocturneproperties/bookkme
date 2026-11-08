package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.api.email.operation.StartEmailChange
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.fromAcceptLanguage
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class EmailChangeRequest(
    @ProtoNumber(1) val email: String,
)

@Serializable
internal class EmailConfirmation(
    @ProtoNumber(1) val verificationId: Uuid,
    @ProtoNumber(2) val code: String,
)

internal fun Route.emailChange() {
    authenticate {
        /**
         * Request a code to change the account's email
         *
         * Summary: Request a code to change the account's email
         * Description: Email a 6-digit code, valid for 10 minutes, to the new address, written in the language of Accept-Language. The address is stored only once the code is confirmed. Only the newest code works.
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.microservice.route.api.EmailChangeRequest] The new address
         * Response: 200 application/x-protobuf [library.email.EmailVerificationStarted] The verification to confirm with
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Email change errors<br>EMAIL_EXIST (1) This email already exists<br>INVALID_EMAIL_FORMAT (2) Invalid email format<br>EMAIL_UNDELIVERABLE (910005) This address can't receive email
         * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Code sending limit<br>EMAIL_CODE_RESEND_TOO_SOON (910004) A code was sent to this address too recently
         * See: docs/operations/authorization/start-email-change.md
         */
        post<Api.Auth.Email> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<EmailChangeRequest>()
            val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
            val startEmailChange by application.injectScoped<StartEmailChange>(AuthScope)

            call.respondWith(startEmailChange(principal.userId, body.email, language))
        }
        /**
         * Confirm the account's new email with the code
         *
         * Summary: Confirm the account's new email with the code
         * Description: Check the emailed code against the caller's newest email change and store the new address.
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.microservice.route.api.EmailConfirmation] The verification and its code
         * Response: 204 application/x-protobuf The new address is stored
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Email confirmation errors<br>EMAIL_CODE_INVALID (910001) The code is wrong<br>EMAIL_CODE_EXPIRED (910002) The code has expired or was already used, request a new one<br>EMAIL_CODE_ATTEMPTS_EXCEEDED (910003) Too many wrong codes, request a new one<br>EMAIL_EXIST (1) This email already exists
         * See: docs/operations/authorization/confirm-email-change.md
         */
        post<Api.Auth.Email.Confirm> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<EmailConfirmation>()
            val confirmEmailChange by application.injectScoped<ConfirmEmailChange>(AuthScope)

            call.respondWith(confirmEmailChange(principal.userId, body.verificationId, body.code))
        }
    }
}
