package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.export.DownloadBusinessDataExport
import com.bookk.business.domain.api.export.GetBusinessDataExport
import com.bookk.business.domain.api.export.StartBusinessDataExport
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
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

internal fun Route.businessDataExport() {
    authenticate {
        /**
         * Start business data export
         *
         * Summary: Start business data export
         * Description: Starts building a JSON copy of everything the business holds (profile, services, employees, pending invitations, clients, appointment settings, appointments and pending requests), for its owner only. The owner confirms it with a passkey action confirmation token issued for EXPORT_BUSINESS_DATA with the business id as business and target. The document is built in the background, a push notification and the status route tell when it is ready. While an export is being built, the same export is returned. One export can be started per day, a failed one can be started again at once
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.microservice.route.api.DataExportRequest]
         * Response: 200 application/x-protobuf [library.export.DataExport] The export being built
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Export not confirmed<br>BUSINESS_ACTION_CONFIRMATION_INVALID (200040) Confirm exporting the business's data with your passkey (action confirmation token from POST auth/passkey/confirm_action missing, expired, already used or issued for another action or business)
         * Response: 404 application/x-protobuf The caller doesn't own the business
         * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Export already made today<br>DATA_EXPORT_TOO_FREQUENT (900002) Only one data export can be started per day
         * See: docs/operations/business/start-business-data-export.md
         */
        post<Api.Business.Id.Export> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val confirmationToken = call.receiveNullable<DataExportRequest>()?.confirmationToken.orEmpty()
            val startBusinessDataExport by application.injectScoped<StartBusinessDataExport>(BusinessScope)

            call.respondWith(startBusinessDataExport(principal.userId, path.parent.id, confirmationToken))
        }

        /**
         * Get business data export
         *
         * Summary: Get business data export
         * Description: The business's latest export, for its owner. NONE when there is none or it expired, RUNNING while it is built, READY with the time its document is deleted, or FAILED. availableAgainAt tells when the next export can be started
         * Tag: business
         * Security: jwt
         * Response: 200 application/x-protobuf [library.export.DataExport] The business's export
         * Response: 404 application/x-protobuf The caller doesn't own the business
         */
        get<Api.Business.Id.Export> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getBusinessDataExport by application.injectScoped<GetBusinessDataExport>(BusinessScope)

            call.respondWith(getBusinessDataExport(principal.userId, path.parent.id))
        }

        /**
         * Download business data export
         *
         * Summary: Download business data export
         * Description: The business's ready export as a JSON file, for its owner. It can be downloaded again until it expires, 72 hours after it was built. Sent gzip-encoded when the request accepts gzip
         * Tag: business
         * Security: jwt
         * Response: 200 application/json The export document
         * Response: 404 application/x-protobuf No export is ready, or the caller doesn't own the business
         */
        get<Api.Business.Id.Export.Download> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val downloadBusinessDataExport by application.injectScoped<DownloadBusinessDataExport>(BusinessScope)

            downloadBusinessDataExport(principal.userId, path.parent.parent.id)
                .onSuccess { call.respondDataExport(it, fileName = "bookk-business-data.json") }
                .onFailure { call.respondWith(Result.failure<Unit>(it)) }
        }
    }
}
