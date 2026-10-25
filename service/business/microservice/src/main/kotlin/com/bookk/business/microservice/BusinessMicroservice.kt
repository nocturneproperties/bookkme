package com.bookk.business.microservice

import com.bookk.business.data.di.businessDataModule
import com.bookk.business.domain.api.BUSINESS_SERVICE_NAME
import com.bookk.business.domain.api.business.operation.DeleteDayOffsInThePast
import com.bookk.business.domain.api.employee.operation.DeleteProcessedEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.ExpireEmployeeInvitations
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.domain.impl.di.businessDomainModule
import com.bookk.business.microservice.route.businessRoute
import com.bookk.core.data.cache.impl.di.cacheModule
import com.bookk.core.data.eventstreaming.data.di.outboxModule
import com.bookk.core.data.eventstreaming.di.embeddedEventStreamingTransportModule
import com.bookk.core.data.eventstreaming.di.eventStreamingTransportModule
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
import library.signing.impl.di.signingModule
import org.koin.dsl.module
import kotlin.time.Duration.Companion.days

fun businessModule() = module {
    includes(
        businessDomainModule(),
        businessDataModule(),
        cacheModule(),
        eventStreamingTransportModule(BusinessScope, BUSINESS_SERVICE_NAME),
        outboxModule(BusinessScope),
        signingModule(BusinessScope)
    )
}

fun businessEmbeddedModule() = module {
    includes(
        businessDomainModule(),
        businessDataModule(),
        cacheModule(),
        embeddedEventStreamingTransportModule(BusinessScope, BUSINESS_SERVICE_NAME),
        outboxModule(BusinessScope),
        signingModule(BusinessScope)
    )
}

fun main() {
    startServer(diModules = listOf(businessModule())) { routing ->
        with(routing.application) {
            val scope = installServiceScope(BusinessScope)
            install(Scheduler) { registerBusinessJobs(this) }
            startEventHandling(scope)
        }
        with(routing) {
            installNegotiation()
            businessRoute()
        }
    }
}

fun Application.registerBusinessJobs(scheduler: SchedulerConfiguration) {
    val scope = requireNotNull(serviceScope(BusinessScope))
    scheduler.job("business-rotateSigningKeys", interval = 7.days) {
        scope.get<RotateSigningKeys>().invoke(retireInterval = 7.days).getOrThrow()
    }
    scheduler.job("deleteDayOffsInThePast", interval = 1.days) {
        scope.get<DeleteDayOffsInThePast>().invoke().getOrThrow()
    }
    scheduler.job("expireEmployeeInvitations", interval = 1.days) {
        scope.get<ExpireEmployeeInvitations>().invoke().getOrThrow()
    }
    scheduler.job("deleteProcessedEmployeeInvitations", interval = 1.days) {
        scope.get<DeleteProcessedEmployeeInvitations>().invoke().getOrThrow()
    }
}
