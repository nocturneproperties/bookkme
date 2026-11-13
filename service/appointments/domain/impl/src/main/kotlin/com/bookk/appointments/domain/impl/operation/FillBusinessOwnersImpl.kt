package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.FillBusinessOwners
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.BusinessClient
import kotlin.uuid.Uuid

internal class FillBusinessOwnersImpl(
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val deletedUserDataSource: DeletedUserDataSource,
    private val businessClient: BusinessClient,
    private val transactionManager: TransactionManager,
) : FillBusinessOwners {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val missing = transactionManager.transactionOrThrow { subscriptionDataSource.getBusinessIdsWithoutOwner() }
        missing
            .mapNotNull { fillOwner(it).exceptionOrNull() }
            .reduceOrNull { first, next -> first.apply { addSuppressed(next) } }
            ?.let { throw it }
    }

    private suspend fun fillOwner(businessId: Uuid): Result<Unit> {
        val business = businessClient.getBusinessById(businessId).getOrElse { error ->
            return if (error.isUnknownBusiness()) detach(businessId) else Result.failure(error)
        }
        val ownerUserId = business.ownerUserId
            ?: return Result.failure(IllegalStateException("Business $businessId came without its owner"))
        return transactionManager.transaction {
            if (deletedUserDataSource.isDeletedForShare(ownerUserId)) {
                subscriptionDataSource.detachBusiness(businessId)
            } else {
                subscriptionDataSource.fillMissingOwner(businessId, ownerUserId)
            }
        }
    }

    private suspend fun detach(businessId: Uuid): Result<Unit> = transactionManager.transaction {
        subscriptionDataSource.detachBusiness(businessId)
    }

    private fun Throwable.isUnknownBusiness(): Boolean {
        return this is Error.NotFound || (this is BusinessError && code == BusinessErrorCodes.BUSINESS_NOT_FOUND)
    }
}
