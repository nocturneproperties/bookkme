package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.RemoveSavedBusiness
import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class RemoveSavedBusinessImpl(
    private val savedBusinessDataSource: SavedBusinessDataSource,
    private val transactionManager: TransactionManager,
) : RemoveSavedBusiness {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<Unit> = transactionManager.transaction {
        savedBusinessDataSource.remove(userId, businessId)
    }
}
