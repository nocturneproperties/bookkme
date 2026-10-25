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
     * Summary: Get sign up challenge
     * Description: Get sign up challenge for passkey creation.
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.registration.entity.CreateAccountRequest] User parameters
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse] Validation challenge returned
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Registration errors<br>EMAIL_EXIST (200001) This email already exists<br>INVALID_EMAIL_FORMAT (200002) Invalid email format
     * See: docs/operations/authorization/sign-up-challenge.md
     */
    post<Api.Auth.PassKey.SignUpChallenge> {
        val info = call.receive<CreateAccountRequest>()
        val startRegistration by application.injectScoped<StartRegistration>(AuthScope)

        call.respondWith(startRegistration(info))
    }
    /**
     * Summary: Validate passkey data
     * Description: Validate passkey data from native credentials API
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest] User parameters
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] User has been created
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Registration errors<br>USER_ALREADY_EXIST (200003) User with this email already exist<br>INVALID_EMAIL_FORMAT (200002) Invalid email format<br>ACCOUNT_CREATION_FAILED (200004) Error during account creation try again later<br>CHALLENGE_WINDOW_EXPIRED (300001) Challenge window expired<br>VERIFICATION_FAILED (300002) Passkey verification failed
     * See: docs/operations/authorization/sign-up.md
     */
    post<Api.Auth.SignUp> {
        val info = call.receive<VerifyAccountCreationRequest>()
        val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
        val finishRegistration by application.injectScoped<FinishRegistration>(AuthScope)
        call.respondWith(finishRegistration(info, language))
    }
}
