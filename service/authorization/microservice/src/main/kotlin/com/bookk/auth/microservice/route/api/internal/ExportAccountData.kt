package com.bookk.auth.microservice.route.api.internal

import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.export.ExportAccountData
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.exportAccountData() {
    /**
     * Summary: Export a user's account data
     * Description: The account's creation and last activity times, its passkeys (name and dates, never key material) and its devices. Used by the user service to build the user's personal data export
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.server.auth.client.export.AccountDataExport] Account data
     * Response: 404 application/x-protobuf The user has no account
     */
    get<Api.Internal.Export.Account> { account ->
        val exportAccountData by application.injectScoped<ExportAccountData>(AuthScope)
        call.respondWith(exportAccountData(account.userId))
    }
}
