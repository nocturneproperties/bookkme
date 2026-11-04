package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest
import com.bookk.auth.domain.api.actionconfirmation.operation.ConfirmAction
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

internal fun Route.confirmAction() {
    authenticate {
        /**
         * Confirm a sensitive action with a passkey
         *
         * Summary: Confirm a sensitive action with a passkey
         * Description: Verify a fresh passkey assertion of the caller's own passkey (challenge from GET passkey/sign_in/challenge) and return a single-use action confirmation token for one action on one target, valid for 2 minutes
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest]
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken] Action confirmation token
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Action confirmation errors<br>CHALLENGE_WINDOW_EXPIRED (6) Challenge window expired<br>VERIFICATION_FAILED (4) Passkey verification failed<br>PASSKEY_OWNER_NOT_FOUND (7) Passkey owner not found<br>PASSKEY_NOT_OWNED (11) Passkey belongs to another account
         * See: docs/operations/authorization/action-confirmation.md
         */
        post<Api.Auth.PassKey.ConfirmAction> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<ActionConfirmationRequest>()
            val confirmAction by application.injectScoped<ConfirmAction>(AuthScope)

            call.respondWith(confirmAction(principal.userId, body))
        }
    }
}
