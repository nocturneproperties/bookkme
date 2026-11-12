package com.bookk.notifications.client.di

import com.bookk.core.client.internalHttpClient
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.notifications.client.impl.ExportUserNotificationDataClientImpl
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val NOTIFICATIONS_HTTP_CLIENT = "notificationsHttpClient"

fun notificationsClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped(named(NOTIFICATIONS_HTTP_CLIENT)) {
            internalHttpClient("APPLICATION_NOTIFICATIONS_SERVICE_HOSTNAME", clientTag)
        }
        scoped<ExportUserNotificationData> {
            ExportUserNotificationDataClientImpl(get(named(NOTIFICATIONS_HTTP_CLIENT)))
        }
    }
}
