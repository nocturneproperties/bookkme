package com.bookk.appointments.microservice.route.api

import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

internal fun Route.healthCheck() {
    /**
     * Summary: Health check
     * Description: Check service health
     * Tag: appointment
     * Response: 200 Service is alive
     */
    get<Api.Appointment.HealthCheck> {
        call.respond(HttpStatusCode.OK)
    }
}
