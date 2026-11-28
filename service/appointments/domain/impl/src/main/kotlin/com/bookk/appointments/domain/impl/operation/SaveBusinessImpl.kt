package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.SaveBusiness
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SaveBusinessImpl(
    private val deletedUserDataSource: DeletedUserDataSource,
    private val savedBusinessDataSource: SavedBusinessDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val transactionManager: TransactionManager,
) : SaveBusiness {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<Unit> = transactionManager.transaction {
        if (deletedUserDataSource.isDeletedForShare(userId)) throw Error.OperationNotAllowed()
        val isEnabled = subscriptionDataSource.isBusinessEnabledForShare(businessId)
        if (savedBusinessDataSource.isSaved(userId, businessId)) return@transaction
        if (!isEnabled) throw SaveBusiness.Error.AppointmentsNotEnabled()
        if (savedBusinessDataSource.count(userId) >= SaveBusiness.MAX_SAVED_BUSINESSES) {
            throw SaveBusiness.Error.LimitReached()
        }
        savedBusinessDataSource.save(userId, businessId, Clock.System.now())
    }
}
