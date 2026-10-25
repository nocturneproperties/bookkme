package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.authentication.entity.VerifySignInRequest
import com.bookk.auth.domain.api.authentication.operation.SignIn
import com.bookk.auth.domain.api.authentication.operation.StartAssertion
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.fromAcceptLanguage
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.signIn() {
    /**
     * Summary: Get sign in challenge
     * Description: Get sign in passkey challenge.
     * Tag: auth
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.authentication.entity.AssertionStartResponse] Passkey json payload
     */
    get<Api.Auth.PassKey.SignInChallenge> {
        val startAssertion: StartAssertion by application.injectScoped<StartAssertion>(AuthScope)
        call.respondWith(startAssertion())
    }
    /**
     * Summary: Verify sign in
     * Description: Verify user-signed challenge and return authentication tokens
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.authentication.entity.VerifySignInRequest]
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] Authentication tokens
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Sign in errors<br>CHALLENGE_WINDOW_EXPIRED (300001) Challenge window expired<br>VERIFICATION_FAILED (300002) Passkey verification failed<br>PASSKEY_OWNER_NOT_FOUND (300003) Passkey owner not found
     * See: docs/operations/authorization/sign-in.md
     */
    post<Api.Auth.SignIn> {
        val request = call.receive<VerifySignInRequest>()
        val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
        val startSigningIn: SignIn by application.injectScoped<SignIn>(AuthScope)
        call.respondWith(startSigningIn(request, language))
    }
}
