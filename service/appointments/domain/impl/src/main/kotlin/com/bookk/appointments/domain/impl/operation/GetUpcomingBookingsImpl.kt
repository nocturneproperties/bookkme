package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.UpcomingBookings
import com.bookk.appointments.domain.api.operation.GetUpcomingBookings
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class GetUpcomingBookingsImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager,
) : GetUpcomingBookings {
    override suspend fun invoke(userId: Uuid): Result<UpcomingBookings> = transactionManager.transaction {
        val now = Clock.System.now()
        val appointments = appointmentDataSource.getUpcomingOfClientUser(userId, now, GetUpcomingBookings.LIMIT)
        UpcomingBookings(
            appointments = appointments.map { it.copy(note = "") },
            requests = requestDataSource.getUpcomingPendingOfUser(userId, now, GetUpcomingBookings.LIMIT),
        )
    }
}
