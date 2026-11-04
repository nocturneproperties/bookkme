package com.bookk.auth.microservice

import com.bookk.auth.data.di.authDataModule
import com.bookk.auth.domain.api.AUTH_SERVICE_NAME
import com.bookk.auth.domain.api.authentication.operation.HandleDormantAccounts
import com.bookk.auth.domain.api.device.operation.DeleteInactiveDevices
import com.bookk.auth.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.auth.domain.api.erasure.operation.ReapplyAccountErasures
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.domain.impl.di.authDomainModule
import com.bookk.auth.microservice.route.authRoute
import com.bookk.core.data.cache.impl.di.cacheModule
import com.bookk.core.data.eventstreaming.data.di.outboxModule
import com.bookk.core.data.eventstreaming.di.embeddedEventStreamingTransportModule
import com.bookk.core.data.eventstreaming.di.eventStreamingTransportModule
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.service.di.installServiceScope
import com.bookk.core.service.di.serviceScope
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.startServer
import com.bookk.server.business.client.di.businessClientModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.application
import library.scheduler.Scheduler
import library.scheduler.SchedulerConfiguration
import library.signing.RotateSigningKeys
import library.signing.impl.di.signingModule
import org.koin.dsl.module
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

fun authModule() = module {
    includes(
        authDomainModule(),
        authDataModule(),
        signingModule(AuthScope),
        eventStreamingTransportModule(AuthScope, AUTH_SERVICE_NAME),
        outboxModule(AuthScope),
        cacheModule(),
        businessClientModule(AuthScope, AUTH_SERVICE_NAME),
    )
}

fun authEmbeddedModule() = module {
    includes(
        authDomainModule(),
        authDataModule(),
        signingModule(AuthScope),
        embeddedEventStreamingTransportModule(AuthScope, AUTH_SERVICE_NAME),
        outboxModule(AuthScope),
        cacheModule(),
        businessClientModule(AuthScope, AUTH_SERVICE_NAME),
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
    scheduler.job("$AUTH_SERVICE_NAME-rotateSigningKeys", interval = 1.days) {
        scope.get<RotateSigningKeys>().invoke(retireInterval = 7.days).getOrThrow()
    }
    scheduler.job("$AUTH_SERVICE_NAME-deleteInactiveDevices", interval = 1.days) {
        scope.get<DeleteInactiveDevices>().invoke().getOrThrow()
    }
    scheduler.job("$AUTH_SERVICE_NAME-handleDormantAccounts", interval = 1.days) {
        scope.get<HandleDormantAccounts>().invoke().getOrThrow()
    }
    scheduler.job("$AUTH_SERVICE_NAME-reapplyErasures", interval = 1.minutes) {
        scope.get<ReapplyAccountErasures>().invoke().getOrThrow()
    }
    scheduler.job("$AUTH_SERVICE_NAME-deleteExpiredErasureRecords", interval = 1.days) {
        scope.get<DeleteExpiredErasureRecords>().invoke().getOrThrow()
    }
}
