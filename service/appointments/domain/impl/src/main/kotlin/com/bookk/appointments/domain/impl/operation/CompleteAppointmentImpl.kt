package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.BusinessClient
import library.permissions.PermissionAction
import library.permissions.assertOrSelf
import kotlin.uuid.Uuid

internal class CompleteAppointmentImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val businessClient: BusinessClient,
    private val transactionManager: TransactionManager
) : CompleteAppointment {

    override suspend fun invoke(
        userId: Uuid,
        appointmentId: Uuid,
        priceAdjustment: PriceAdjustmentDraft?
    ): Result<Appointment> = runSuspendCatching {
        priceAdjustment?.throwIfInvalid()
        return transactionManager.transaction {
            val appointment = appointmentDataSource.getForUpdate(appointmentId)

            appointmentPermissionDataSource
                .getPermission(userId, appointment.businessId)
                .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = appointment.employee.userId)

            appointment.requireCompletable()
            val adjustment = priceAdjustment?.let { resolveAdjustment(appointment, it) }

            val completed = when (appointment.status) {
                AppointmentStatus.COMPLETED -> appointment
                else -> appointmentDataSource.markCompletedByUser(appointmentId)
            }
            val adjusted = adjustment?.let {
                appointmentDataSource.adjustPrice(appointmentId, it)
            }
            adjusted ?: completed
        }
    }

    private fun PriceAdjustmentDraft.throwIfInvalid() {
        if (price.isNegative) throw CompleteAppointment.Error.NegativePrice()
        if (reason.orEmpty().length > PriceAdjustmentDraft.REASON_MAX_LENGTH) throw CompleteAppointment.Error.ReasonTooLong()
    }

    private suspend fun resolveAdjustment(appointment: Appointment, draft: PriceAdjustmentDraft): PriceAdjustment {
        if (draft.price.currencyUnit != appointment.totalAmount.currencyUnit) throw CompleteAppointment.Error.CurrencyMismatch()
        return PriceAdjustment(
            additionalServices = resolveAdditionalServices(appointment.businessId, draft.additionalServiceIds),
            price = draft.price,
            reason = draft.reason?.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun resolveAdditionalServices(businessId: Uuid, serviceIds: List<Uuid>): List<ServiceSnapshot> {
        if (serviceIds.isEmpty()) return emptyList()
        return businessClient.getServicesByIds(businessId, serviceIds).getOrThrow().map {
            ServiceSnapshot(id = it.id, name = it.name, groupId = it.group.id, price = it.price, duration = it.duration)
        }
    }
}
