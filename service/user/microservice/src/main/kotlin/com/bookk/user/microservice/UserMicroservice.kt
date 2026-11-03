package com.bookk.user.microservice

import com.bookk.core.data.cache.impl.di.cacheModule
import com.bookk.core.data.eventstreaming.data.di.outboxModule
import com.bookk.core.data.eventstreaming.di.embeddedEventStreamingTransportModule
import com.bookk.core.data.eventstreaming.di.eventStreamingTransportModule
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.service.di.installServiceScope
import com.bookk.core.service.di.serviceScope
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.startServer
import com.bookk.user.data.di.userDataModule
import com.bookk.user.domain.api.USER_SERVICE_NAME
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.domain.impl.di.userDomainModule
import com.bookk.user.microservice.route.userRoute
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.application
import library.export.BuildDataExports
import library.export.DeleteExpiredDataExports
import library.scheduler.Scheduler
import library.scheduler.SchedulerConfiguration
import org.koin.dsl.module
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun userModule() = module {
    includes(
        userDomainModule(),
        userDataModule(),
        cacheModule(),
        eventStreamingTransportModule(UserScope, USER_SERVICE_NAME),
        outboxModule(UserScope),
    )
}

fun userEmbeddedModule() = module {
    includes(
        userDomainModule(),
        userDataModule(),
        cacheModule(),
        embeddedEventStreamingTransportModule(UserScope, USER_SERVICE_NAME),
        outboxModule(UserScope),
    )
}

fun main() {
    startServer(diModules = listOf(userModule())) { routing ->
        with(routing.application) {
            val scope = installServiceScope(UserScope)
            install(Scheduler) { registerUserJobs(this) }
            startEventHandling(scope)
        }
        with(routing) {
            installNegotiation()
            userRoute()
        }
    }
}

fun Application.registerUserJobs(scheduler: SchedulerConfiguration) {
    val scope = requireNotNull(serviceScope(UserScope))
    scheduler.job("$USER_SERVICE_NAME-buildDataExports", interval = 1.minutes) {
        scope.get<BuildDataExports>().invoke().getOrThrow()
    }
    scheduler.job("$USER_SERVICE_NAME-deleteExpiredDataExports", interval = 1.hours) {
        scope.get<DeleteExpiredDataExports>().invoke().getOrThrow()
    }
}
