package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.delete_account.operation.CancelAccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.GetAccountDeletion
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.accountDeletion() {
    authenticate {
        /**
         * Get account deletion
         *
         * Summary: Get account deletion
         * Description: Returns when the caller's account will be deleted, or no date when no deletion is pending. A deletion requested with DELETE /api/auth/account runs 7 days later unless it is cancelled
         * Tag: auth
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.delete_account.entity.AccountDeletionStatus] Pending deletion date, empty when none is pending
         */
        get<Api.Auth.Account.Deletion> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getAccountDeletion by application.injectScoped<GetAccountDeletion>(AuthScope)

            call.respondWith(getAccountDeletion(principal.userId))
        }

        /**
         * Cancel account deletion
         *
         * Summary: Cancel account deletion
         * Description: Cancels the caller's pending account deletion and tells them by notification. Succeeds without a notification when no deletion is pending
         * Tag: auth
         * Security: jwt
         * Response: 204 application/x-protobuf Deletion cancelled, or none was pending
         * See: docs/operations/authorization/cancel-account-deletion.md
         */
        delete<Api.Auth.Account.Deletion> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val cancelAccountDeletion by application.injectScoped<CancelAccountDeletion>(AuthScope)

            call.respondWith(cancelAccountDeletion(principal.userId))
        }
    }
}
