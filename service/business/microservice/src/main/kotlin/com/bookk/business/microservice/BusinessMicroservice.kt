package com.bookk.business.microservice

import com.bookk.business.data.di.businessDataModule
import com.bookk.business.domain.api.BUSINESS_SERVICE_NAME
import com.bookk.business.domain.api.business.operation.DeleteDayOffsInThePast
import com.bookk.business.domain.api.client.operation.DeleteInactiveClients
import com.bookk.business.domain.api.employee.operation.DeleteProcessedEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.ExpireEmployeeInvitations
import com.bookk.business.domain.api.encryption.operation.EncryptPlaintextContacts
import com.bookk.business.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.business.domain.api.erasure.operation.ReapplyClientErasures
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.domain.impl.di.businessDomainModule
import com.bookk.business.microservice.route.businessRoute
import com.bookk.core.data.cache.impl.di.cacheModule
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
import library.actionconfirmation.impl.di.actionConfirmationModule
import library.export.BuildDataExports
import library.export.DeleteExpiredDataExports
import library.outbox.di.outboxModule
import library.scheduler.Scheduler
import library.scheduler.SchedulerConfiguration
import library.signing.RotateSigningKeys
import library.signing.impl.di.signingModule
import library.signing.route.jwks
import org.koin.dsl.module
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun businessModule(environment: (String) -> String? = System::getenv) = module {
    includes(
        businessDomainModule(),
        businessDataModule(environment),
        actionConfirmationModule(BusinessScope),
        cacheModule(),
        eventStreamingTransportModule(BusinessScope, BUSINESS_SERVICE_NAME),
        outboxModule(BusinessScope),
        signingModule(BusinessScope),
    )
}

fun businessEmbeddedModule(environment: (String) -> String? = System::getenv) = module {
    includes(
        businessDomainModule(),
        businessDataModule(environment),
        actionConfirmationModule(BusinessScope),
        cacheModule(),
        embeddedEventStreamingTransportModule(BusinessScope, BUSINESS_SERVICE_NAME),
        outboxModule(BusinessScope),
        signingModule(BusinessScope),
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
            jwks(BusinessScope)
            businessRoute()
        }
    }
}

fun Application.registerBusinessJobs(scheduler: SchedulerConfiguration) {
    val scope = requireNotNull(serviceScope(BusinessScope))
    scheduler.job("$BUSINESS_SERVICE_NAME-rotateSigningKeys", interval = 7.days) {
        scope.get<RotateSigningKeys>().invoke(retireInterval = 7.days).getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-deleteDayOffsInThePast", interval = 1.days) {
        scope.get<DeleteDayOffsInThePast>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-expireEmployeeInvitations", interval = 1.days) {
        scope.get<ExpireEmployeeInvitations>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-deleteProcessedEmployeeInvitations", interval = 1.days) {
        scope.get<DeleteProcessedEmployeeInvitations>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-deleteInactiveClients", interval = 1.days) {
        scope.get<DeleteInactiveClients>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-reapplyErasures", interval = 1.minutes) {
        scope.get<ReapplyClientErasures>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-deleteExpiredErasureRecords", interval = 1.days) {
        scope.get<DeleteExpiredErasureRecords>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-buildDataExports", interval = 1.minutes) {
        scope.get<BuildDataExports>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-deleteExpiredDataExports", interval = 1.hours) {
        scope.get<DeleteExpiredDataExports>().invoke().getOrThrow()
    }
    scheduler.job("$BUSINESS_SERVICE_NAME-encryptPlaintextContacts", interval = 10.minutes) {
        scope.get<EncryptPlaintextContacts>().invoke().getOrThrow()
    }
}
