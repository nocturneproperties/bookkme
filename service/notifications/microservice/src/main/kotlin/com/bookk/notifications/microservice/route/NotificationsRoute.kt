package com.bookk.notifications.microservice.route

import com.bookk.notifications.microservice.route.api.healthCheck
import com.bookk.notifications.microservice.route.api.notificationSettings
import com.bookk.notifications.microservice.route.api.notificationToken
import io.ktor.server.routing.Routing

fun Routing.notificationsRoute() {
    healthCheck()
    notificationToken()
    notificationSettings()
}
