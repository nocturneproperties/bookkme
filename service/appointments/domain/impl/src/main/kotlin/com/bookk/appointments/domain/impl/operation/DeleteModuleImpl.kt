package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteModule
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class DeleteModuleImpl(
    private val dataSource: AppointmentSubscriptionDataSource,
    private val transactionManager: TransactionManager
) : DeleteModule {
    override suspend fun invoke(businessId: Uuid): Result<Unit> = transactionManager.transaction {
        dataSource.detachBusiness(businessId)
    }
}