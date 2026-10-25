package com.bookk.notifications.microservice.route

import io.ktor.resources.Resource
import kotlin.uuid.Uuid

object NotificationsRouting {
    @Resource("api")
    class Api {

        @Resource("/notifications")
        class Notification(val parent: Api = Api()) {

            @Resource("/healthcheck")
            class HealthCheck(val parent: Notification = Notification())

            @Resource("/{deviceUuid}/token")
            class Token(val parent: Notification = Notification(), val deviceUuid: Uuid)

            @Resource("/settings")
            class Settings(val parent: Notification = Notification())
        }
    }
}
