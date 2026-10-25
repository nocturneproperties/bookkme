package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServicesByIdsRequest
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe

internal fun Route.getServicesByIds() {
    /**
     * Summary: Get services by ids
     * Description: Resolve services of this business by id, in request order, repeating a service requested more than once. Used by other services to snapshot a verified service price and duration
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.business.domain.api.service.entity.ServicesByIdsRequest]
     */
    post<Api.Internal.Business.Id.Services> { resource ->
        val body = call.receive<ServicesByIdsRequest>()
        val getServicesByIds by application.injectScoped<GetServicesByIds>(BusinessScope)

        call.respondWith(getServicesByIds(businessId = resource.parent.id, serviceIds = body.serviceIds))
    }.describe {
        responses {
            response(HttpStatusCode.OK.value) {
                schema = jsonSchema<List<Service>>()
                description = "Resolved services"
                ContentType.Application.ProtoBuf()
            }
            response(HttpStatusCode.UnprocessableEntity.value) {
                schema = jsonSchema<SimpleServerError>()
                description = "Get services by ids errors<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found"
                ContentType.Application.ProtoBuf()
            }
        }
    }
}
