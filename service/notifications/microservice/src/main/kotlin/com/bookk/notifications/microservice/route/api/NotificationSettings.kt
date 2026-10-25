package com.bookk.notifications.microservice.route.api

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.UpdateNotificationSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.impl.di.NotificationsScope
import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.notificationSettings() {
    authenticate {
        /**
         * Summary: Get notification settings
         * Description: Get notification settings for authenticated user
         * Tag: notification
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.notifications.domain.api.entity.NotificationSettings] Notification settings
         */
        get<Api.Notification.Settings> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getNotificationSettings by application.injectScoped<GetNotificationSettings>(NotificationsScope)
            call.respondWith(getNotificationSettings(principal.userId))
        }

        /**
         * Summary: Update notification settings
         * Description: Update notification settings for authenticated user
         * Tag: notification
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.notifications.domain.api.entity.NotificationSettings.Update]
         * Response: 200 application/x-protobuf [com.bookk.notifications.domain.api.entity.NotificationSettings] Updated notification settings
         * See: docs/operations/notifications/update-notification-settings.md
         */
        put<Api.Notification.Settings> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<NotificationSettings.Update>()
            val updateNotificationSettings by application.injectScoped<UpdateNotificationSettings>(NotificationsScope)
            call.respondWith(updateNotificationSettings(principal.userId, body))
        }
    }
}
