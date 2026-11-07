package com.bookk.appointments.microservice.route.api.internal

import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import io.ktor.server.resources.get
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.exportUserAppointments() {
    /**
     * Export a user's appointments
     *
     * Summary: Export a user's appointments
     * Description: Every appointment and request where the user is the client, the employee or the one who booked, with the user's roles in each. Client details, notes and reasons appear only where the user is the client. Used by the user service to build the user's personal data export
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport] Appointments of the user
     */
    get<Api.Internal.Export.User> { user ->
        val exportUserAppointments by application.injectScoped<ExportUserAppointments>(AppointmentsScope)
        call.respondWith(exportUserAppointments(user.userId))
    }
}
