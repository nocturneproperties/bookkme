package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.delete_account.entity.DeleteAccountRequest
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveNullable
import io.ktor.server.resources.delete
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.deleteAccount() {
    authenticate {
        /**
         * Delete Account
         *
         * Summary: Delete Account
         * Description: Delete the caller's account and associated data with a single-use action confirmation token for DELETE_ACCOUNT, the nil UUID as business id and the caller's user id as target
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.delete_account.entity.DeleteAccountRequest]
         * Response: 204 application/x-protobuf Account deleted
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] ACTION_CONFIRMATION_INVALID (12) Missing, invalid, expired, already used or mismatched action confirmation token
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] VERIFICATION_FAILED (4) Invalid credentials
         * See: docs/operations/authorization/delete-account.md
         */
        delete<Api.Auth.Account> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val confirmationToken = call.receiveNullable<DeleteAccountRequest>()?.confirmationToken.orEmpty()
            val deleteAccount by application.injectScoped<DeleteAccount>(AuthScope)

            call.respondWith(deleteAccount(principal.userId, confirmationToken))
        }
    }
}
