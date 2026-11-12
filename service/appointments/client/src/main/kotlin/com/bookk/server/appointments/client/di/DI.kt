package com.bookk.server.appointments.client.di

import com.bookk.core.client.internalHttpClient
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import com.bookk.server.appointments.client.impl.ExportBusinessAppointmentsClientImpl
import com.bookk.server.appointments.client.impl.ExportUserAppointmentsClientImpl
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APPOINTMENTS_HTTP_CLIENT = "appointmentsHttpClient"

fun appointmentsClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped(named(APPOINTMENTS_HTTP_CLIENT)) {
            internalHttpClient("APPLICATION_APPOINTMENTS_SERVICE_HOSTNAME", clientTag)
        }
        scoped<ExportUserAppointments> { ExportUserAppointmentsClientImpl(get(named(APPOINTMENTS_HTTP_CLIENT))) }
        scoped<ExportBusinessAppointments> {
            ExportBusinessAppointmentsClientImpl(get(named(APPOINTMENTS_HTTP_CLIENT)))
        }
    }
}
