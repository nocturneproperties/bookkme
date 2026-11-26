package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeFinishRequest
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeStartRequest
import com.bookk.auth.domain.api.demo.operation.FinishDemoUpgrade
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.fromAcceptLanguage
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.demoUpgrade() {
    authenticate(optional = true) {
        /**
         * Start the upgrade of a demo account
         *
         * Summary: Start the upgrade of a demo account
         * Description: Email a 6-digit code to the address the demo account will keep and return the options to create its passkey. Send the access token while the demo runs; in the 7 days after it ended, send no Authorization header and put the device's refresh token in refreshToken instead. The email may be the one an earlier attempt already stored for this account. The code email is written in the language of Accept-Language.
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.demo.entity.DemoUpgradeStartRequest] The name and email to keep, and the refresh token when no access token is sent
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.registration.entity.SignUpChallengeResponse] Passkey creation options and the email verification to confirm with
         * Response: 401 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Neither a valid access token nor a refresh token, or demo errors<br>INVALID_CREDENTIALS (8) Invalid credentials<br>DEMO_EXPIRED (27) The demo period of this account has ended
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Demo upgrade errors<br>EMAIL_EXIST (1) This email already exists<br>INVALID_EMAIL_FORMAT (2) Invalid email format<br>EMAIL_DOMAIN_UNSUPPORTED (17) Email addresses on this domain are not supported<br>NOT_A_DEMO_ACCOUNT (29) This account is not a demo account<br>EMAIL_UNDELIVERABLE (910005) This address can't receive email
         * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Code sending limit<br>EMAIL_CODE_RESEND_TOO_SOON (910004) A code was sent to this address too recently
         * See: docs/operations/authorization/demo-upgrade.md
         */
        post<Api.Auth.Demo.UpgradeChallenge> {
            val body = call.receive<DemoUpgradeStartRequest>()
            val caller = call.demoCaller(body.refreshToken) ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
            val startDemoUpgrade by application.injectScoped<StartDemoUpgrade>(AuthScope)

            call.respondWith(startDemoUpgrade(caller, body.name, body.email, language))
        }
        /**
         * Finish the upgrade of a demo account
         *
         * Summary: Finish the upgrade of a demo account
         * Description: Check the emailed code and the new passkey, store the verified email and the name in place of the demo's placeholders, end the demo and sign the device in again. The account keeps all its data, its sample business stops being a demo, and the returned tokens no longer carry the demo claim, so replace both stored tokens. Identify the caller like the start step.
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.demo.entity.DemoUpgradeFinishRequest] The passkey registration, the email code and the name, and the refresh token when no access token is sent
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] New tokens of the upgraded account
         * Response: 401 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Neither a valid access token nor a refresh token, or demo errors<br>INVALID_CREDENTIALS (8) Invalid credentials<br>DEMO_EXPIRED (27) The demo period of this account has ended
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Demo upgrade errors<br>EMAIL_EXIST (1) This email already exists<br>VERIFICATION_FAILED (4) Passkey verification failed<br>CHALLENGE_WINDOW_EXPIRED (6) Challenge window expired<br>NOT_A_DEMO_ACCOUNT (29) This account is not a demo account<br>EMAIL_CODE_INVALID (910001) The code is wrong<br>EMAIL_CODE_EXPIRED (910002) The code has expired, request a new one<br>EMAIL_CODE_ATTEMPTS_EXCEEDED (910003) Too many wrong codes, request a new one
         * See: docs/operations/authorization/demo-upgrade.md
         */
        post<Api.Auth.Demo.Upgrade> {
            val body = call.receive<DemoUpgradeFinishRequest>()
            val caller = call.demoCaller(body.refreshToken) ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val finishDemoUpgrade by application.injectScoped<FinishDemoUpgrade>(AuthScope)

            call.respondWith(finishDemoUpgrade(caller, body))
        }
    }
}

private fun ApplicationCall.demoCaller(refreshToken: String?): DemoCaller? {
    val principal = principal<AppPrincipal>()
    if (principal != null) return DemoCaller.AccessToken(principal.authId, principal.deviceId)
    return refreshToken?.takeIf { it.isNotBlank() }?.let { DemoCaller.RefreshToken(it) }
}
