package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AdditionalService
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.CompleteAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.business.domain.api.service.entity.Service
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
    private val transactionManager: TransactionManager,
) : CompleteAppointment {

    override suspend fun invoke(
        userId: Uuid,
        appointmentId: Uuid,
        priceAdjustment: PriceAdjustmentDraft?,
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
        if (reason.orEmpty().length > PriceAdjustmentDraft.REASON_MAX_LENGTH) {
            throw CompleteAppointment.Error.ReasonTooLong()
        }
        if (!additionalServices.isValidSelection()) throw CompleteAppointment.Error.AdditionalServicesInvalid()
    }

    private fun List<RequestedService>.isValidSelection(): Boolean {
        val serviceIds = map { it.serviceId }
        val totalCount = sumOf { it.count.toLong() }
        val hasOnlyPositiveCounts = all { it.count > 0 }
        val hasDistinctServices = serviceIds.distinct().size == serviceIds.size
        return hasOnlyPositiveCounts && hasDistinctServices &&
            totalCount <= PriceAdjustmentDraft.MAX_ADDITIONAL_SERVICES
    }

    private suspend fun resolveAdjustment(appointment: Appointment, draft: PriceAdjustmentDraft): PriceAdjustment {
        if (draft.price.currencyUnit != appointment.totalAmount.currencyUnit) {
            throw CompleteAppointment.Error.CurrencyMismatch()
        }
        return PriceAdjustment(
            additionalServices = resolveAdditionalServices(appointment.businessId, draft.additionalServices),
            price = draft.price,
            reason = draft.reason?.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun resolveAdditionalServices(
        businessId: Uuid,
        requested: List<RequestedService>,
    ): List<AdditionalService> {
        if (requested.isEmpty()) return emptyList()
        val snapshotsById = businessClient
            .getServicesByIds(businessId, requested.map { it.serviceId })
            .getOrThrow()
            .associate { it.id to it.snapshot() }
        return requested.map { AdditionalService(service = snapshotsById.getValue(it.serviceId), count = it.count) }
    }

    private fun Service.snapshot(): ServiceSnapshot {
        return ServiceSnapshot(id = id, name = name, groupId = group.id, price = price, duration = duration)
    }
}
