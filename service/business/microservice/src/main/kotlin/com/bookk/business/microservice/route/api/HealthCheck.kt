package com.bookk.business.microservice.route.api

import com.bookk.business.microservice.route.BusinessRouting.Api
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

internal fun Route.healthCheck() {
    /**
     * Summary: Health check
     * Description: Check service health
     * Tag: business
     * Response: 200 Service is alive
     */
    get<Api.Business.HealthCheck> {
        call.respond(HttpStatusCode.OK)
    }
}
