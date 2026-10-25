package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.operation.EnableAppointmentsForBusiness
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.onConstraintFailure
import com.bookk.server.business.client.api.BusinessClient
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

internal class EnableAppointmentsForBusinessImpl(
    private val subscriptionSource: AppointmentSubscriptionDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val businessClient: BusinessClient,
    private val transactionManager: TransactionManager
) : EnableAppointmentsForBusiness {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<Unit> {
        val permission = businessClient.getPermission(userId, businessId, BusinessResource.BUSINESS).getOrElse { return Result.failure(it) }
        val business = businessClient.getBusinessById(businessId).getOrElse { return Result.failure(it) }
        return transactionManager.transaction<Unit> {
            if (!permission.covers(ResourcePermission.FULL)) throw Error.OperationNotAllowed()
            subscriptionSource.attachBusiness(
                BusinessSnapshot(
                    id = business.id,
                    name = business.name,
                    address = business.address,
                    timeZone = business.timeZone,
                    isEnabled = true,
                    schedule = business.schedule
                )
            )
            appointmentPermissionDataSource.setPermission(userId, businessId, ResourcePermission.FULL, suspended = false)
            settingsDataSource.create(AppointmentSettings(businessId, business.timeZone))
        }.onConstraintFailure {
            throw EnableAppointmentsForBusiness.Error.AlreadyEnabled()
        }
    }
}
