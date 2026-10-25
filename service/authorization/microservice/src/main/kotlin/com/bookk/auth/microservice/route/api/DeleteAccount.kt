package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.delete_account.entity.VerifyDeleteAccRequest
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.deleteAccount() {
    authenticate {
        /**
         * Summary: Delete Account
         * Description: Delete user account and all associated data attached to current auth token
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.delete_account.entity.VerifyDeleteAccRequest]
         * Response: 200 application/x-protobuf Success
         * See: docs/operations/authorization/delete-account.md
         */
        delete<Api.Auth.Account> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<VerifyDeleteAccRequest>()
            val deleteAccount by application.injectScoped<DeleteAccount>(AuthScope)

            call.respondWith(deleteAccount(principal.userId, body))
        }
    }
}
