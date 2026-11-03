package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.exportUserBusinessData() {
    /**
     * Summary: Export a user's business data
     * Description: The business the user owns, their employee rows (suspended ones included) with permissions, working hours and days off, their client rows as each business keeps them, and the business their dashboard shows. Used by the user service to build the user's personal data export
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.business.domain.api.export.BusinessUserDataExport] Business data of the user
     */
    get<Api.Internal.Export.User> { user ->
        val exportUserBusinessData by application.injectScoped<ExportUserBusinessData>(BusinessScope)
        call.respondWith(exportUserBusinessData(user.userId))
    }
}
