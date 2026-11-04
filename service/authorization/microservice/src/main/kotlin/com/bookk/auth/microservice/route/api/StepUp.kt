package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.stepup.entity.StepUpRequest
import com.bookk.auth.domain.api.stepup.operation.IssueStepUpToken
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.stepUp() {
    authenticate {
        /**
         * Confirm a sensitive action with a passkey
         *
         * Summary: Confirm a sensitive action with a passkey
         * Description: Verify a fresh passkey assertion of the caller's own passkey (challenge from GET passkey/sign_in/challenge) and return a single-use step-up token for one action on one target, valid for 2 minutes
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.stepup.entity.StepUpRequest]
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.stepup.entity.StepUpToken] Step-up token
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Step-up errors<br>CHALLENGE_WINDOW_EXPIRED (6) Challenge window expired<br>VERIFICATION_FAILED (4) Passkey verification failed<br>PASSKEY_OWNER_NOT_FOUND (7) Passkey owner not found<br>PASSKEY_NOT_OWNED (11) Passkey belongs to another account
         * See: docs/operations/authorization/step-up.md
         */
        post<Api.Auth.PassKey.StepUp> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<StepUpRequest>()
            val issueStepUpToken by application.injectScoped<IssueStepUpToken>(AuthScope)

            call.respondWith(issueStepUpToken(principal.userId, body))
        }
    }
}
