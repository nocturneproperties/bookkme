package com.bookk.server

import com.bookk.appointments.microservice.route.appointmentsRoute
import com.bookk.auth.microservice.route.authRoute
import com.bookk.business.microservice.route.businessRoute
import com.bookk.notifications.microservice.route.notificationsRoute
import com.bookk.user.microservice.route.userRoute
import io.ktor.server.routing.Routing

internal fun Routing.monolithRoute() {
    authRoute()
    userRoute()
    businessRoute()
    appointmentsRoute()
    notificationsRoute()
}
