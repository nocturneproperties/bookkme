package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteBusinessAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

internal class DeleteBusinessAppointmentDataImpl(
    private val dataSource: AppointmentSubscriptionDataSource,
    private val subjectKeys: SubjectKeys,
    private val transactionManager: TransactionManager,
) : DeleteBusinessAppointmentData {
    override suspend fun invoke(businessId: Uuid): Result<Unit> = transactionManager.transaction {
        val businessIds = listOf(businessId)
        val clientIds = dataSource.getClientIdsForUpdate(businessIds)
        dataSource.detachBusinesses(businessIds)
        subjectKeys.shredAll(clientIds)
    }
}
