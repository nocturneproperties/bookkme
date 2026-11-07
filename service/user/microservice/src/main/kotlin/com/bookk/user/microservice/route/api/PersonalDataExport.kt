package com.bookk.user.microservice.route.api

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.operation.DownloadPersonalDataExport
import com.bookk.user.domain.api.operation.GetPersonalDataExport
import com.bookk.user.domain.api.operation.StartPersonalDataExport
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveNullable
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.export.route.respondDataExport

@Serializable
internal class DataExportRequest(
    @ProtoNumber(1) val confirmationToken: String?,
)

internal fun Route.personalDataExport() {
    authenticate {
        /**
         * Start personal data export
         *
         * Summary: Start personal data export
         * Description: Starts building a JSON copy of everything Bookk holds about the caller, from every service. The caller confirms it with a passkey action confirmation token issued for EXPORT_PERSONAL_DATA, the nil business id and the caller's own user id as target. The document is built in the background, a push notification and the status route tell when it is ready. While an export is being built, the same export is returned. One export can be started per day, a failed one can be started again at once
         * Tag: user
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.user.microservice.route.api.DataExportRequest]
         * Response: 200 application/x-protobuf [library.export.DataExport] The export being built
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Export not confirmed<br>USER_ACTION_CONFIRMATION_INVALID (100005) Confirm exporting your data with your passkey (action confirmation token from POST auth/passkey/confirm_action missing, expired, already used or issued for another action)
         * Response: 404 application/x-protobuf The caller's profile does not exist
         * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Export already made today<br>DATA_EXPORT_TOO_FREQUENT (900002) Only one data export can be started per day
         * See: docs/operations/user/start-personal-data-export.md
         */
        post<Api.User.Export> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val confirmationToken = call.receiveNullable<DataExportRequest>()?.confirmationToken.orEmpty()
            val startPersonalDataExport by application.injectScoped<StartPersonalDataExport>(UserScope)

            call.respondWith(startPersonalDataExport(principal.userId, confirmationToken))
        }

        /**
         * Get personal data export
         *
         * Summary: Get personal data export
         * Description: The caller's latest export, NONE when there is none or it expired, RUNNING while it is built, READY with the time its document is deleted, or FAILED. availableAgainAt tells when the next export can be started
         * Tag: user
         * Security: jwt
         * Response: 200 application/x-protobuf [library.export.DataExport] The caller's export
         */
        get<Api.User.Export> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getPersonalDataExport by application.injectScoped<GetPersonalDataExport>(UserScope)

            call.respondWith(getPersonalDataExport(principal.userId))
        }

        /**
         * Download personal data export
         *
         * Summary: Download personal data export
         * Description: The caller's ready export as a JSON file. It can be downloaded again until it expires, 72 hours after it was built. Sent gzip-encoded when the request accepts gzip
         * Tag: user
         * Security: jwt
         * Response: 200 application/json The export document
         * Response: 404 application/x-protobuf No export is ready
         */
        get<Api.User.Export.Download> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val downloadPersonalDataExport by application.injectScoped<DownloadPersonalDataExport>(UserScope)

            downloadPersonalDataExport(principal.userId)
                .onSuccess { call.respondDataExport(it, fileName = "bookk-personal-data.json") }
                .onFailure { call.respondWith(Result.failure<Unit>(it)) }
        }
    }
}
