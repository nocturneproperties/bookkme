package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.SavedBusiness
import com.bookk.appointments.domain.api.operation.GetSavedBusinesses
import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetSavedBusinessesImpl(
    private val savedBusinessDataSource: SavedBusinessDataSource,
    private val transactionManager: TransactionManager,
) : GetSavedBusinesses {
    override suspend fun invoke(userId: Uuid): Result<List<SavedBusiness>> = transactionManager.transaction {
        savedBusinessDataSource.getAll(userId)
    }
}
