package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.CreateService
import com.bookk.business.domain.api.service.operation.DeleteService
import com.bookk.business.domain.api.service.operation.GetServices
import com.bookk.business.domain.api.service.operation.UpdateService
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
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe

fun Route.serviceCrud() {
    authenticate {
        /**
         * Summary: Create a new service
         * Description: Creates a new service offering that can be presented to the clients. The price is always stored in the business currency, the currency sent in the body is ignored. The group must belong to the business, the response carries the stored group
         * Tag: service
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.service.entity.Service]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.service.entity.Service] Created service entity
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create service errors<br>BUSINESS_SERVICE_EXISTS (200007) Service with this name already exists<br>BUSINESS_SERVICE_NAME_VALIDATION_ERROR (200008) Invalid service name<br>BUSINESS_SERVICE_PRICE_PRECISION_INVALID (200037) Service price has more decimal places than the business currency allows<br>BUSINESS_SERVICE_BUSINESS_CURRENCY_INVALID (200038) Business currency is not a supported currency, update the business currency first<br>BUSINESS_SERVICE_GROUP_NOT_EXISTS (200012) Service group does not exist in this business
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/create-service.md
         */
        post<Api.Service> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<Service>()
            val createService by application.injectScoped<CreateService>(BusinessScope)
            if (it.businessId != body.businessId) {
                call.respond(HttpStatusCode.BadRequest, "Bad request")
            } else {
                call.respondWith(
                    createService(requestUserId = principal.userId, service = body)
                )
            }
        }

        /**
         * Summary: Update service
         * Description: Updates a new service offering that can be presented to the clients. The price is always stored in the business currency, the currency sent in the body is ignored. The group must belong to the business, the response carries the stored group
         * Tag: service
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.service.entity.Service]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.service.entity.Service] Updated service entity
         * Response: 400 application/x-protobuf Path business id or service id does not match the body
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User is not allowed to update services of the business, or the service does not belong to it
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Update service errors<br>BUSINESS_SERVICE_EXISTS (200007) Service with this name already exists<br>BUSINESS_SERVICE_NAME_VALIDATION_ERROR (200008) Invalid service name<br>BUSINESS_SERVICE_PRICE_PRECISION_INVALID (200037) Service price has more decimal places than the business currency allows<br>BUSINESS_SERVICE_BUSINESS_CURRENCY_INVALID (200038) Business currency is not a supported currency, update the business currency first<br>BUSINESS_SERVICE_GROUP_NOT_EXISTS (200012) Service group does not exist in this business
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/update-service.md
         */
        put<Api.Service.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<Service>()
            val updateService by application.injectScoped<UpdateService>(BusinessScope)

            if (it.parent.businessId != body.businessId || it.id != body.id) {
                call.respond(HttpStatusCode.BadRequest, "Bad request")
            } else {
                call.respondWith(
                    updateService(requestUserId = principal.userId, service = body)
                )
            }
        }

        /**
         * Summary: Get all services
         * Description: Get all service offerings of a business with specific id
         * Tag: service
         * Security: jwt
         */
        get<Api.Service> {
            val getServices by application.injectScoped<GetServices>(BusinessScope)

            call.respondWith(getServices(it.businessId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<Service>>()
                    description = "List of services"
                    ContentType.Application.ProtoBuf()
                }
            }
        }

        /**
         * Summary: Delete service offering
         * Description: Delete service offering. A service id of another business is left untouched
         * Tag: service
         * Security: jwt
         * Response: 204 application/x-protobuf Service offering deleted
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/delete-service.md
         */
        delete<Api.Service.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val deleteService by application.injectScoped<DeleteService>(BusinessScope)

            call.respondWith(
                deleteService(
                    requestUserId = principal.userId,
                    businessId = it.parent.businessId,
                    id = it.id
                )
            )
        }
    }
}
