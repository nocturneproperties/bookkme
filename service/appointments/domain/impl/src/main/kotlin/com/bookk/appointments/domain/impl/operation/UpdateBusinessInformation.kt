package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.business.client.api.BusinessDTO
import kotlin.time.Instant

internal class UpdateBusinessInformation(
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val transactionManager: TransactionManager
) {

    suspend operator fun invoke(
        business: BusinessDTO,
        updatedAt: Instant
    ) = transactionManager.transaction {
        subscriptionDataSource.updateBusiness(
            snapshot = BusinessSnapshot(
                id = business.id,
                name = business.name,
                address = business.address,
                timeZone = business.timeZone,
                isEnabled = true,
                schedule = business.schedule
            ),
            updatedAt = updatedAt
        )
    }
}
