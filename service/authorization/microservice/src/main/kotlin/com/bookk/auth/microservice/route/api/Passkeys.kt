package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.auth.domain.api.identification.operation.DeletePasskey
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge
import com.bookk.auth.domain.api.identification.operation.GetAvailablePasskeys
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.passkeyOperations() {
    authenticate {
        /**
         * Summary: Get Passkeys
         * Description: Get list of all available passkeys for current auth method authId
         * Tag: auth
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.identification.entity.PasskeyResponse]
         */
        get<Api.Auth.PassKey> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getPasskeys by application.injectScoped<GetAvailablePasskeys>(AuthScope)

            call.respondWith(getPasskeys(principal.authId))
        }
        /**
         * Summary: Get challenge for new passkey
         * Description: Creates challenge for process of attaching passkey to current account
         * Tag: auth
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse] Registration challenge
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Passkey errors<br>UNABLE_TO_GENERATE_REGISTRATION_CHALLENGE (200005) Unable to provide registration challenge
         */
        get<Api.Auth.PassKey.AddChallenge> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getChallenge by application.injectScoped<GetAttachPasskeyToAccountChallenge>(AuthScope)

            call.respondWith(getChallenge(principal.authId, principal.deviceId, principal.userId))
        }
        /**
         * Summary: Add new passkey
         * Description: Adds new passkey to account represented by current auth method
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest] Passkey verification payload
         * Response: 201 application/x-protobuf Passkey created
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Passkey errors<br>LAST_PASSKEY (200006) Can't delete passkey if it's the only one registered
         * See: docs/operations/authorization/add-passkey.md
         */
        post<Api.Auth.PassKey.AddFinish> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AddPasskeyRequest>()
            val attachPasskey by application.injectScoped<AttachNewPasskeyToAccount>(AuthScope)

            call.respondWith(attachPasskey(principal.authId, body))
        }
        /**
         * Summary: Delete passkey
         * Description: Delete passkey by id
         * Tag: auth
         * Security: jwt
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Passkey errors<br>LAST_PASSKEY (200006) Can't delete passkey if it's the only one registered
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Passkey not found
         * Response: 204 application/x-protobuf Passkey deleted
         * See: docs/operations/authorization/delete-passkey.md
         */
        delete<Api.Auth.PassKey.Id> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val deletePasskey by application.injectScoped<DeletePasskey>(AuthScope)
            call.respondWith(deletePasskey(path.id, principal.authId))
        }
    }
}
