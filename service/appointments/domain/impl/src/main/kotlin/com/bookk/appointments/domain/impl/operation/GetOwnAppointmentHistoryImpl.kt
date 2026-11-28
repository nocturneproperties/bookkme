package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentPage
import com.bookk.appointments.domain.api.entity.OwnAppointmentFilter
import com.bookk.appointments.domain.api.operation.GetOwnAppointmentHistory
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.PageRequest
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class GetOwnAppointmentHistoryImpl(
    private val dataSource: AppointmentDataSource,
    private val transactionManager: TransactionManager,
    private val clock: Clock,
) : GetOwnAppointmentHistory {
    override suspend fun invoke(
        userId: Uuid,
        filter: OwnAppointmentFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentPage> {
        val page = PageRequest.of(limit, cursor).getOrElse { return Result.failure(it) }
        return transactionManager.transaction {
            val appointments = dataSource.getPageOfClientUser(userId, filter, clock.now(), page)
            AppointmentPage.of(appointments.map { it.copy(note = "") })
        }
    }
}
