package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getBusinessPermission() {
    /**
     * Summary: Get user permission for business resource
     * Description: The view/update/delete grant the user holds on the given business resource, all false when the user holds none. Used by other services to authorize business scoped actions
     * Tag: internal
     * Response: 200 application/x-protobuf [library.permissions.ResourcePermission] Permission value
     * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
     */
    get<Api.Internal.Business.Id.Permissions> { permissions ->
        val getBusinessPermission by application.injectScoped<GetBusinessPermission>(BusinessScope)
        call.respondWith(getBusinessPermission(permissions.userId, permissions.parent.id, permissions.resource))
    }
}
