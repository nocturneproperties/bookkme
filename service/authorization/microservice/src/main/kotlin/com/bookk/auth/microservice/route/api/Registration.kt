package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.fromAcceptLanguage
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.registration() {
    /**
     * Get sign up challenge
     *
     * Summary: Get sign up challenge
     * Description: Get sign up challenge for passkey creation and email a 6-digit code to the address, valid for 10 minutes. The code email is written in the language of Accept-Language. Calling it again restarts sign-up with a new challenge and a new code.
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.registration.entity.CreateAccountRequest] User parameters
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse] Passkey challenge and the email verification to finish sign-up with
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Registration errors<br>EMAIL_EXIST (1) This email already exists<br>INVALID_EMAIL_FORMAT (2) Invalid email format<br>EMAIL_UNDELIVERABLE (910005) This address can't receive email
     * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Code sending limit<br>EMAIL_CODE_RESEND_TOO_SOON (910004) A code was sent to this address too recently
     * See: docs/operations/authorization/sign-up-challenge.md
     */
    post<Api.Auth.PassKey.SignUpChallenge> {
        val info = call.receive<CreateAccountRequest>()
        val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
        val startRegistration by application.injectScoped<StartRegistration>(AuthScope)

        call.respondWith(startRegistration(info, language))
    }
    /**
     * Validate passkey data
     *
     * Summary: Validate passkey data
     * Description: Validate passkey data from native credentials API together with the emailed code (verificationId and emailCode from the sign up challenge), and create the account with the verified address. userInfo.email is ignored. acceptedDocuments must name the version in effect of every published legal document (GET /api/user/legal), and is recorded with the new account.
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest] User parameters
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] User has been created
     * Response: 400 application/x-protobuf The body is missing a required field, acceptedDocuments, verificationId and emailCode included
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Registration errors<br>USER_ALREADY_EXIST (3) User with this email already exist<br>INVALID_EMAIL_FORMAT (2) Invalid email format<br>ACCOUNT_CREATION_FAILED (5) Error during account creation try again later<br>CHALLENGE_WINDOW_EXPIRED (6) Challenge window expired<br>VERIFICATION_FAILED (4) Passkey verification failed<br>LEGAL_DOCUMENT_VERSION_UNKNOWN (100003) Legal document version is not published<br>LEGAL_DOCUMENTS_NOT_ACCEPTED (100004) Every legal document in effect must be accepted<br>EMAIL_CODE_INVALID (910001) The code is wrong<br>EMAIL_CODE_EXPIRED (910002) The code has expired or was already used, request a new one<br>EMAIL_CODE_ATTEMPTS_EXCEEDED (910003) Too many wrong codes, request a new one
     * See: docs/operations/authorization/sign-up.md
     */
    post<Api.Auth.SignUp> {
        val info = call.receive<VerifyAccountCreationRequest>()
        val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
        val finishRegistration by application.injectScoped<FinishRegistration>(AuthScope)
        call.respondWith(finishRegistration(info, language))
    }
}
