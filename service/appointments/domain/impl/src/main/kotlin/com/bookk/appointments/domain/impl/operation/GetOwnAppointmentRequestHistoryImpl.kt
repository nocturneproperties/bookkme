package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequestPage
import com.bookk.appointments.domain.api.entity.OwnAppointmentRequestFilter
import com.bookk.appointments.domain.api.operation.GetOwnAppointmentRequestHistory
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.PageRequest
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class GetOwnAppointmentRequestHistoryImpl(
    private val dataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager,
    private val clock: Clock,
) : GetOwnAppointmentRequestHistory {
    override suspend fun invoke(
        userId: Uuid,
        filter: OwnAppointmentRequestFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentRequestPage> {
        val page = PageRequest.of(limit, cursor).getOrElse { return Result.failure(it) }
        return transactionManager.transaction {
            AppointmentRequestPage.of(dataSource.getPageOfInitiator(userId, filter, clock.now(), page))
        }
    }
}
