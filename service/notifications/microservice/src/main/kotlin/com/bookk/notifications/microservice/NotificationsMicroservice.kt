package com.bookk.notifications.microservice

import com.bookk.core.AppLevelConstants
import com.bookk.core.data.cache.impl.di.cacheModule
import com.bookk.core.data.eventstreaming.di.embeddedEventStreamingModule
import com.bookk.core.data.eventstreaming.di.eventStreamingModule
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.service.di.installServiceScope
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.startServer
import com.bookk.notifications.data.di.notificationsDataModule
import com.bookk.notifications.domain.api.NOTIFICATIONS_SERVICE_NAME
import com.bookk.notifications.domain.impl.di.NotificationsScope
import com.bookk.notifications.domain.impl.di.notificationsDomainModule
import com.bookk.notifications.microservice.route.notificationsRoute
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.routing.application
import org.koin.dsl.module

fun notificationsModule() = module {
    includes(
        notificationsDomainModule(),
        notificationsDataModule(),
        eventStreamingModule(NotificationsScope, NOTIFICATIONS_SERVICE_NAME),
        cacheModule()
    )
}

fun notificationsEmbeddedModule() = module {
    includes(
        notificationsDomainModule(),
        notificationsDataModule(),
        embeddedEventStreamingModule(NotificationsScope, NOTIFICATIONS_SERVICE_NAME),
        cacheModule()
    )
}

fun main() {
    initFirebase()
    startServer(diModules = listOf(notificationsModule())) { routing ->
        with(routing.application) {
            val scope = installServiceScope(NotificationsScope)
            startEventHandling(scope)
        }
        with(routing) {
            installNegotiation()
            notificationsRoute()
        }
    }
}

fun initFirebase() {
    val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(AppLevelConstants.firebasePrivateKey.byteInputStream()))
        .build()

    if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options)
    }
}
