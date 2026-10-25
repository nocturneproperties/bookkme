package com.bookk.appointments.domain.impl.di

import com.bookk.appointments.domain.api.APPOINTMENTS_SCHEMA
import com.bookk.appointments.domain.api.operation.CancelAppointment
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.api.operation.CreateAppointmentRequest
import com.bookk.appointments.domain.api.operation.DeclineAppointmentRequest
import com.bookk.appointments.domain.api.operation.DeleteModule
import com.bookk.appointments.domain.api.operation.DeleteOutdatedRequests
import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.api.operation.EditSettings
import com.bookk.appointments.domain.api.operation.EnableAppointmentsForBusiness
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.domain.api.operation.GetAppointmentRequests
import com.bookk.appointments.domain.api.operation.GetAppointmentsForDate
import com.bookk.appointments.domain.api.operation.GetClientBusinessesAppointmentsStatus
import com.bookk.appointments.domain.api.operation.GetPendingAppointmentRequests
import com.bookk.appointments.domain.api.operation.GetSettings
import com.bookk.appointments.domain.api.operation.IsAppointmentsEnabled
import com.bookk.appointments.domain.api.operation.MarkAppointmentNoShow
import com.bookk.appointments.domain.api.operation.MarkAppointmentsCompleted
import com.bookk.appointments.domain.api.operation.UpdateAppointment
import com.bookk.appointments.domain.impl.event.AppointmentEventHandler
import com.bookk.appointments.domain.impl.operation.CancelAppointmentImpl
import com.bookk.appointments.domain.impl.operation.CompleteAppointmentImpl
import com.bookk.appointments.domain.impl.operation.CreateAppointmentImpl
import com.bookk.appointments.domain.impl.operation.CreateAppointmentRequestImpl
import com.bookk.appointments.domain.impl.operation.DeclineAppointmentRequestImpl
import com.bookk.appointments.domain.impl.operation.DeleteModuleImpl
import com.bookk.appointments.domain.impl.operation.DeleteOutdatedRequestsImpl
import com.bookk.appointments.domain.impl.operation.DeleteUserAppointmentDataImpl
import com.bookk.appointments.domain.impl.operation.EditSettingsImpl
import com.bookk.appointments.domain.impl.operation.EnableAppointmentsForBusinessImpl
import com.bookk.appointments.domain.impl.operation.GetAppointmentHistoryImpl
import com.bookk.appointments.domain.impl.operation.GetAppointmentRequestsImpl
import com.bookk.appointments.domain.impl.operation.GetAppointmentsForDateImpl
import com.bookk.appointments.domain.impl.operation.GetClientBusinessesAppointmentsStatusImpl
import com.bookk.appointments.domain.impl.operation.GetPendingAppointmentRequestsImpl
import com.bookk.appointments.domain.impl.operation.GetSettingsImpl
import com.bookk.appointments.domain.impl.operation.IsAppointmentsEnabledImpl
import com.bookk.appointments.domain.impl.operation.MarkAppointmentNoShowImpl
import com.bookk.appointments.domain.impl.operation.MarkAppointmentsCompletedImpl
import com.bookk.appointments.domain.impl.operation.SyncEmployeePermission
import com.bookk.appointments.domain.impl.operation.UpdateAppointmentImpl
import com.bookk.appointments.domain.impl.operation.UpdateBusinessInformation
import com.bookk.core.data.eventstreaming.EventHandler
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val AppointmentsScope: Qualifier = named(APPOINTMENTS_SCHEMA)

fun appointmentsDomainModule() = module {
    scope(AppointmentsScope) {
        scopedOf(::CreateAppointmentImpl) bind CreateAppointment::class
        scopedOf(::EnableAppointmentsForBusinessImpl) bind EnableAppointmentsForBusiness::class
        scopedOf(::AppointmentEventHandler) bind EventHandler::class
        scopedOf(::DeleteModuleImpl) bind DeleteModule::class
        scopedOf(::EditSettingsImpl) bind EditSettings::class
        scopedOf(::GetSettingsImpl) bind GetSettings::class
        scopedOf(::CreateAppointmentRequestImpl) bind CreateAppointmentRequest::class
        scopedOf(::GetAppointmentRequestsImpl) bind GetAppointmentRequests::class
        scopedOf(::GetPendingAppointmentRequestsImpl) bind GetPendingAppointmentRequests::class
        scopedOf(::GetAppointmentsForDateImpl) bind GetAppointmentsForDate::class
        scopedOf(::GetAppointmentHistoryImpl) bind GetAppointmentHistory::class
        scopedOf(::UpdateAppointmentImpl) bind UpdateAppointment::class
        scopedOf(::CancelAppointmentImpl) bind CancelAppointment::class
        scopedOf(::MarkAppointmentNoShowImpl) bind MarkAppointmentNoShow::class
        scopedOf(::CompleteAppointmentImpl) bind CompleteAppointment::class
        scopedOf(::UpdateBusinessInformation)
        scopedOf(::SyncEmployeePermission)
        scopedOf(::DeclineAppointmentRequestImpl) bind DeclineAppointmentRequest::class
        scopedOf(::IsAppointmentsEnabledImpl) bind IsAppointmentsEnabled::class
        scopedOf(::GetClientBusinessesAppointmentsStatusImpl) bind GetClientBusinessesAppointmentsStatus::class
        scopedOf(::MarkAppointmentsCompletedImpl) bind MarkAppointmentsCompleted::class
        scopedOf(::DeleteOutdatedRequestsImpl) bind DeleteOutdatedRequests::class
        scopedOf(::DeleteUserAppointmentDataImpl) bind DeleteUserAppointmentData::class
    }
}
