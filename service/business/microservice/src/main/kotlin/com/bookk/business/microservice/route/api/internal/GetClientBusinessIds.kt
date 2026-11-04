package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getClientBusinessIds() {
    /**
     * Get businesses for client
     *
     * Summary: Get businesses for client
     * Description: Ids of every business the given user is a registered client of. Used by other services to resolve which businesses a user has a client relationship with
     * Tag: internal
     * Response: 200 application/protobuf [kotlin.uuid.Uuid]+ Business ids
     */
    get<Api.Internal.Client.Businesses> { businesses ->
        val getClientBusinessIds by application.injectScoped<GetClientBusinessIds>(BusinessScope)
        call.respondWith(getClientBusinessIds(businesses.userId))
    }
}
