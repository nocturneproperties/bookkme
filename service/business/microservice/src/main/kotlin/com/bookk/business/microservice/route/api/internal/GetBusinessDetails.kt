package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getBusinessDetails() {
    /**
     * Summary: Get business details
     * Description: Get business with its working schedule, without any caller-specific info (permissions, employee id, client id are always empty). Used by other services to replicate business information
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.Business] Business associated with id
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Business errors<br>BUSINESS_NOT_FOUND (200003) Business with this id is missing
     */
    get<Api.Internal.Business.Id> { business ->
        val getBusinessDetails by application.injectScoped<GetBusinessDetails>(BusinessScope)
        call.respondWith(getBusinessDetails(business.id))
    }
}
