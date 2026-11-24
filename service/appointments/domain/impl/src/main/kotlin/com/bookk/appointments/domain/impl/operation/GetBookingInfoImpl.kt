package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.BookingInfo
import com.bookk.appointments.domain.api.operation.GetBookingInfo
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import kotlin.uuid.Uuid

internal class GetBookingInfoImpl(
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val transactionManager: TransactionManager,
) : GetBookingInfo {
    override suspend fun invoke(businessId: Uuid): Result<BookingInfo> = transactionManager.transaction {
        if (!subscriptionDataSource.isBusinessEnabled(businessId)) throw Error.NotFound()
        val settings = settingsDataSource.get(businessId) ?: throw Error.NotFound()
        BookingInfo(businessId = businessId, appointmentNote = settings.appointmentNote)
    }
}
