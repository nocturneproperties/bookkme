package com.bookk.notifications.microservice

import com.bookk.core.AppLevelConstants
import com.bookk.core.data.cache.impl.di.cacheModule
import com.bookk.core.data.eventstreaming.di.embeddedEventStreamingTransportModule
import com.bookk.core.data.eventstreaming.di.eventStreamingTransportModule
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.service.di.installServiceScope
import com.bookk.core.service.di.serviceScope
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.startServer
import com.bookk.notifications.data.di.notificationsDataModule
import com.bookk.notifications.domain.api.EncryptPlaintextTargets
import com.bookk.notifications.domain.api.NOTIFICATIONS_SERVICE_NAME
import com.bookk.notifications.domain.impl.di.NotificationsScope
import com.bookk.notifications.domain.impl.di.notificationsDomainModule
import com.bookk.notifications.microservice.route.notificationsRoute
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.application
import library.email.impl.di.emailModule
import library.scheduler.Scheduler
import library.scheduler.SchedulerConfiguration
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

fun notificationsModule(environment: (String) -> String? = System::getenv) = module {
    includes(
        notificationsDomainModule(),
        notificationsDataModule(environment),
        emailModule(NotificationsScope, environment),
        eventStreamingTransportModule(NotificationsScope, NOTIFICATIONS_SERVICE_NAME),
        cacheModule(),
    )
}

fun notificationsEmbeddedModule(environment: (String) -> String? = System::getenv) = module {
    includes(
        notificationsDomainModule(),
        notificationsDataModule(environment),
        emailModule(NotificationsScope, environment),
        embeddedEventStreamingTransportModule(NotificationsScope, NOTIFICATIONS_SERVICE_NAME),
        cacheModule(),
    )
}

fun main() {
    initFirebase()
    startServer(diModules = listOf(notificationsModule())) { routing ->
        with(routing.application) {
            val scope = installServiceScope(NotificationsScope)
            install(Scheduler) { registerNotificationsJobs(this) }
            startEventHandling(scope)
        }
        with(routing) {
            installNegotiation()
            notificationsRoute()
        }
    }
}

fun Application.registerNotificationsJobs(scheduler: SchedulerConfiguration) {
    val scope = requireNotNull(serviceScope(NotificationsScope))
    scheduler.job("$NOTIFICATIONS_SERVICE_NAME-encryptPlaintextTargets", interval = 10.minutes) {
        scope.get<EncryptPlaintextTargets>().invoke().getOrThrow()
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
