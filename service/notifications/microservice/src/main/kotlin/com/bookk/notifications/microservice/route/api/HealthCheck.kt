package com.bookk.notifications.microservice.route.api

import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

internal fun Route.healthCheck() {
    /**
     * Summary: Health check
     * Description: Check service health
     * Tag: notification
     * Response: 200 Service is alive
     */
    get<Api.Notification.HealthCheck> {
        call.respond(HttpStatusCode.OK)
    }
}
