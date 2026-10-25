package com.bookk.appointments.microservice.route

import com.bookk.appointments.microservice.route.api.appointment
import com.bookk.appointments.microservice.route.api.appointmentInit
import com.bookk.appointments.microservice.route.api.healthCheck
import com.bookk.appointments.microservice.route.api.requests
import com.bookk.appointments.microservice.route.api.settings
import io.ktor.server.routing.Routing

fun Routing.appointmentsRoute() {
    healthCheck()
    appointmentInit()
    appointment()
    settings()
    requests()
}