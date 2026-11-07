package com.bookk.notifications.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.notifications.domain.impl.di.NotificationsScope
import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import io.ktor.server.resources.get
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.exportUserNotificationData() {
    /**
     * Export a user's notification data
     *
     * Summary: Export a user's notification data
     * Description: The user's notification settings and channels, their devices (language and whether push is registered, never the push token) and the email and Telegram targets notifications are sent to. Used by the user service to build the user's personal data export
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.notifications.client.api.export.NotificationsUserDataExport] Notification data of the user
     */
    get<Api.Internal.Export.User> { user ->
        val exportUserNotificationData by application.injectScoped<ExportUserNotificationData>(NotificationsScope)
        call.respondWith(exportUserNotificationData(user.userId))
    }
}
