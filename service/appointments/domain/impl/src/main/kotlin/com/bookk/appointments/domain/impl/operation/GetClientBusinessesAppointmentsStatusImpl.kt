package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.BusinessAppointmentsEnabled
import com.bookk.appointments.domain.api.operation.GetClientBusinessesAppointmentsStatus
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.business.client.api.BusinessClient
import kotlin.uuid.Uuid

internal class GetClientBusinessesAppointmentsStatusImpl(
    private val businessClient: BusinessClient,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val transactionManager: TransactionManager
) : GetClientBusinessesAppointmentsStatus {
    override suspend fun invoke(userId: Uuid): Result<List<BusinessAppointmentsEnabled>> {
        val businessIds = businessClient.getClientBusinessIds(userId).getOrElse { return Result.failure(it) }

        return transactionManager.transaction {
            businessIds.map { businessId ->
                BusinessAppointmentsEnabled(
                    businessId = businessId,
                    enabled = subscriptionDataSource.isBusinessEnabled(businessId)
                )
            }
        }
    }
}
