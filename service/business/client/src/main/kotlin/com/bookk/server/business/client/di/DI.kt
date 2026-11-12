package com.bookk.server.business.client.di

import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.core.client.internalHttpClient
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.impl.BusinessClientImpl
import com.bookk.server.business.client.impl.operation.ExportUserBusinessDataClientImpl
import com.bookk.server.business.client.impl.operation.GetAppointmentBookingContextClientImpl
import com.bookk.server.business.client.impl.operation.GetAppointmentRescheduleContextClientImpl
import com.bookk.server.business.client.impl.operation.GetBusinessDetailsClientImpl
import com.bookk.server.business.client.impl.operation.GetBusinessPermissionClientImpl
import com.bookk.server.business.client.impl.operation.GetClientBusinessIdsClientImpl
import com.bookk.server.business.client.impl.operation.GetOwnedBusinessMemberIdsClientImpl
import com.bookk.server.business.client.impl.operation.GetServicesByIdsClientImpl
import com.bookk.server.business.client.impl.operation.GetStaffBookingContextClientImpl
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val BUSINESS_HTTP_CLIENT = "businessHttpClient"

fun businessClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped(named(BUSINESS_HTTP_CLIENT)) { internalHttpClient("APPLICATION_BUSINESS_SERVICE_HOSTNAME", clientTag) }
        scoped<GetBusinessDetails> { GetBusinessDetailsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetBusinessPermission> { GetBusinessPermissionClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetAppointmentBookingContext> {
            GetAppointmentBookingContextClientImpl(get(named(BUSINESS_HTTP_CLIENT)))
        }
        scoped<GetStaffBookingContext> { GetStaffBookingContextClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetAppointmentRescheduleContext> {
            GetAppointmentRescheduleContextClientImpl(get(named(BUSINESS_HTTP_CLIENT)))
        }
        scoped<GetClientBusinessIds> { GetClientBusinessIdsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetServicesByIds> { GetServicesByIdsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetOwnedBusinessMemberIds> { GetOwnedBusinessMemberIdsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<ExportUserBusinessData> { ExportUserBusinessDataClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<BusinessClient> { BusinessClientImpl(get(), get(), get(), get(), get(), get(), get(), get()) }
    }
}
