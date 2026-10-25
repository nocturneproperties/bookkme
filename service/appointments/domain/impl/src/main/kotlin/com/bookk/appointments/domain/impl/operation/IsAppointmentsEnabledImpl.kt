package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.IsAppointmentsEnabled
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.onPermissionsMissingReturn
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class IsAppointmentsEnabledImpl(
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager
) : IsAppointmentsEnabled {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<Boolean> = transactionManager.transaction {
        appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)
        subscriptionDataSource.isBusinessEnabled(businessId)
    }.onPermissionsMissingReturn { false }
}