package com.bookk.auth.microservice

import com.bookk.auth.data.di.authDataModule
import com.bookk.auth.domain.api.AUTH_SERVICE_NAME
import com.bookk.auth.domain.api.device.operation.DeleteInactiveDevices
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.domain.impl.di.authDomainModule
import com.bookk.auth.microservice.route.authRoute
import com.bookk.core.data.cache.impl.di.cacheModule
import com.bookk.core.data.eventstreaming.di.embeddedEventStreamingModule
import com.bookk.core.data.eventstreaming.di.eventStreamingModule
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.service.di.installServiceScope
import com.bookk.core.service.di.serviceScope
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.startServer
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.application
import library.scheduler.Scheduler
import library.scheduler.SchedulerConfiguration
import library.signing.RotateSigningKeys
import org.koin.dsl.module
import kotlin.time.Duration.Companion.days

fun authModule() = module {
    includes(
        authDomainModule(),
        authDataModule(),
        eventStreamingModule(AuthScope, AUTH_SERVICE_NAME),
        cacheModule()
    )
}

fun authEmbeddedModule() = module {
    includes(
        authDomainModule(),
        authDataModule(),
        embeddedEventStreamingModule(AuthScope, AUTH_SERVICE_NAME),
        cacheModule()
    )
}

fun main() {
    startServer(diModules = listOf(authModule())) { routing ->
        with(routing.application) {
            val scope = installServiceScope(AuthScope)
            install(Scheduler) { registerAuthJobs(this) }
            startEventHandling(scope)
        }
        with(routing) {
            installNegotiation()
            authRoute()
        }
    }
}

fun Application.registerAuthJobs(scheduler: SchedulerConfiguration) {
    val scope = requireNotNull(serviceScope(AuthScope))
    scheduler.job("rotateSigningKeys", interval = 1.days) {
        scope.get<RotateSigningKeys>().invoke(retireInterval = 7.days).getOrThrow()
    }
    scheduler.job("deleteInactiveDevices", interval = 1.days) {
        scope.get<DeleteInactiveDevices>().invoke().getOrThrow()
    }
}
