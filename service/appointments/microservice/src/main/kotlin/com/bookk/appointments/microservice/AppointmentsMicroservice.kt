package com.bookk.appointments.microservice

import com.bookk.appointments.data.di.appointmentsDataModule
import com.bookk.appointments.domain.api.APPOINTMENTS_SERVICE_NAME
import com.bookk.appointments.domain.api.operation.DeleteOutdatedRequests
import com.bookk.appointments.domain.api.operation.MarkAppointmentsCompleted
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.domain.impl.di.appointmentsDomainModule
import com.bookk.appointments.microservice.route.appointmentsRoute
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
import org.koin.dsl.module
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun appointmentsModule() = module {
    includes(
        appointmentsDomainModule(),
        appointmentsDataModule(),
        cacheModule(),
        eventStreamingTransportModule(AppointmentsScope, APPOINTMENTS_SERVICE_NAME),
        outboxModule(AppointmentsScope),
        businessClientModule(AppointmentsScope, APPOINTMENTS_SERVICE_NAME)
    )
}

fun appointmentsEmbeddedModule() = module {
    includes(
        appointmentsDomainModule(),
        appointmentsDataModule(),
        cacheModule(),
        embeddedEventStreamingTransportModule(AppointmentsScope, APPOINTMENTS_SERVICE_NAME),
        outboxModule(AppointmentsScope),
        businessClientModule(AppointmentsScope, APPOINTMENTS_SERVICE_NAME)
    )
}

fun main() {
    startServer(diModules = listOf(appointmentsModule())) { routing ->
        with(routing.application) {
            val scope = installServiceScope(AppointmentsScope)
            install(Scheduler) {
                registerAppointmentsJobs(this)
            }
            startEventHandling(scope)
        }
        with(routing) {
            installNegotiation()
            appointmentsRoute()
        }
    }
}

fun Application.registerAppointmentsJobs(scheduler: SchedulerConfiguration) {
    val scope = requireNotNull(serviceScope(AppointmentsScope))
    scheduler.job("$APPOINTMENTS_SERVICE_NAME-markAppointmentsAsCompleted", interval = 5.minutes) {
        scope.get<MarkAppointmentsCompleted>().invoke().getOrThrow()
    }
    scheduler.job("$APPOINTMENTS_SERVICE_NAME-deleteOutdatedRequests", interval = 1.hours) {
        scope.get<DeleteOutdatedRequests>().invoke().getOrThrow()
    }
}
