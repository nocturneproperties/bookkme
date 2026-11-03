package com.bookk.appointments.microservice.route.api.internal

import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.route.AppointmentsRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import io.ktor.server.resources.get
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.exportBusinessAppointments() {
    /**
     * Summary: Export a business's appointments
     * Description: The business's appointment settings, every appointment and every pending request, with client and employee snapshots, times, services, prices, adjustments, status, notes and reasons. Used by the business service to build the business data export
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport] Appointments of the business
     */
    get<Api.Internal.Export.Business> { business ->
        val exportBusinessAppointments by application.injectScoped<ExportBusinessAppointments>(AppointmentsScope)
        call.respondWith(exportBusinessAppointments(business.businessId))
    }
}
