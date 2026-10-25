package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.service.operation.CreateServiceGroup
import com.bookk.business.domain.api.service.operation.DeleteServiceGroup
import com.bookk.business.domain.api.service.operation.GetServiceGroups
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe

fun Route.serviceGroupCrud() {
    authenticate {
        /**
         * Summary: Create a new service group
         * Description: Creates a new service group that can be presented to the clients
         * Tag: service_group
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.service.entity.ServiceGroup]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.service.entity.ServiceGroup] Created service entity
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create service group errors<br>BUSINESS_SERVICE_GROUP_EXISTS (200010) Service group with this name already exists<br>BUSINESS_SERVICE_GROUP_VALIDATION_ERROR (200011) Invalid service group name
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/create-service-group.md
         */
        post<Api.ServiceGroup> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<ServiceGroup>()
            val createServiceGroup by application.injectScoped<CreateServiceGroup>(BusinessScope)

            if (it.businessId != body.businessId) {
                call.respond(HttpStatusCode.BadRequest, "Bad request")
            } else {
                call.respondWith(
                    createServiceGroup(
                        requestUserId = principal.userId,
                        service = body
                    )
                )
            }
        }

        /**
         * Summary: Get all service groups
         * Description: Get all service groups of a business with specific id
         * Tag: service_group
         * Security: jwt
         */
        get<Api.ServiceGroup> {
            val getGroups by application.injectScoped<GetServiceGroups>(BusinessScope)

            call.respondWith(getGroups(it.businessId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<ServiceGroup>>()
                    description = "List of service groups"
                    ContentType.Application.ProtoBuf()
                }
            }
        }

        /**
         * Summary: Delete service group
         * Description: Delete service group with all services that belongs to it. A group id of another business is left untouched
         * Tag: service_group
         * Security: jwt
         * Response: 204 application/x-protobuf Service group deleted
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/delete-service-group.md
         */
        delete<Api.ServiceGroup.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val deleteGroup by application.injectScoped<DeleteServiceGroup>(BusinessScope)

            call.respondWith(
                deleteGroup(
                    requestUserId = principal.userId,
                    businessId = it.parent.businessId,
                    id = it.id
                )
            )
        }
    }
}
