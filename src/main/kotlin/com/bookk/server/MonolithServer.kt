package com.bookk.server

import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.appointmentsEmbeddedModule
import com.bookk.appointments.microservice.registerAppointmentsJobs
import com.bookk.appointments.microservice.route.appointmentsRoute
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.authEmbeddedModule
import com.bookk.auth.microservice.registerAuthJobs
import com.bookk.auth.microservice.route.authRoute
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.businessEmbeddedModule
import com.bookk.business.microservice.registerBusinessJobs
import com.bookk.business.microservice.route.businessRoute
import com.bookk.core.data.eventstreaming.di.topicQueueHolderModule
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.service.di.installServiceScope
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.startServer
import com.bookk.notifications.domain.impl.di.NotificationsScope
import com.bookk.notifications.microservice.initFirebase
import com.bookk.notifications.microservice.notificationsEmbeddedModule
import com.bookk.notifications.microservice.route.notificationsRoute
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.userRoute
import com.bookk.user.microservice.userEmbeddedModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.application
import library.scheduler.Scheduler
import library.scheduler.SchedulerConfiguration

private fun diModules() = listOf(
    topicQueueHolderModule(),
    authEmbeddedModule(),
    userEmbeddedModule(),
    businessEmbeddedModule(),
    appointmentsEmbeddedModule(),
    notificationsEmbeddedModule()
)

fun main() {
    initFirebase()
    startServer(diModules = diModules()) { routing ->
        with(routing.application) {
            val authScope = installServiceScope(AuthScope)
            val userScope = installServiceScope(UserScope)
            val businessScope = installServiceScope(BusinessScope)
            val appointmentsScope = installServiceScope(AppointmentsScope)
            val notificationsScope = installServiceScope(NotificationsScope)
            install(Scheduler) { registerMonolithJobs(this) }
            startEventHandling(authScope, userScope, businessScope, appointmentsScope, notificationsScope)
        }
        with(routing) {
            installNegotiation()
            authRoute()
            userRoute()
            businessRoute()
            appointmentsRoute()
            notificationsRoute()
        }
    }
}

fun Application.registerMonolithJobs(scheduler: SchedulerConfiguration) {
    registerAuthJobs(scheduler)
    registerBusinessJobs(scheduler)
    registerAppointmentsJobs(scheduler)
}
