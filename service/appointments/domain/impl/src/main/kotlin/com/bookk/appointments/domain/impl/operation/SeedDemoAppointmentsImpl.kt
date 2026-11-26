package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.operation.SeedDemoAppointments
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.BusinessDTO
import io.ktor.http.HttpStatusCode
import library.permissions.ResourcePermission
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SeedDemoAppointmentsImpl(
    private val businessClient: BusinessClient,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val deletedUserDataSource: DeletedUserDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : SeedDemoAppointments {

    private val employeeBookings = EmployeeBookings(appointmentDataSource)

    override suspend fun invoke(
        businessId: Uuid,
        ownerUserId: Uuid,
        employeeId: Uuid,
        serviceIds: List<Uuid>,
        clientIds: List<Uuid>,
    ): Result<Unit> = runSuspendCatching {
        if (serviceIds.isEmpty() || clientIds.isEmpty()) return@runSuspendCatching
        val business = businessClient.getBusinessById(businessId).getOrElse { failure ->
            if (failure.isGone()) return@runSuspendCatching
            throw failure
        }
        val contexts = clientIds.map { clientId ->
            businessClient.getStaffBookingContext(businessId, employeeId, clientId, serviceIds).getOrThrow()
        }
        transactionManager.transactionOrThrow {
            if (deletedUserDataSource.isDeletedForShare(ownerUserId)) return@transactionOrThrow
            if (settingsDataSource.get(businessId) != null) return@transactionOrThrow
            enableAppointments(business)
            val settings = requireNotNull(settingsDataSource.getForUpdate(businessId))
            seed(DemoSeed(business, settings, contexts), Clock.System.now())
        }
    }

    private suspend fun enableAppointments(business: BusinessDTO) {
        subscriptionDataSource.attachBusiness(
            BusinessSnapshot(
                id = business.id,
                name = business.name,
                address = business.address,
                timeZone = business.timeZone,
                isEnabled = true,
                schedule = business.schedule,
                ownerUserId = business.ownerUserId,
            ),
        )
        appointmentPermissionDataSource.setPermission(
            business.ownerUserId,
            business.id,
            ResourcePermission.FULL,
            suspended = false,
        )
        settingsDataSource.create(AppointmentSettings(business.id, business.timeZone))
    }

    private suspend fun seed(seed: DemoSeed, now: Instant) {
        DemoBookingPlan.appointmentStarts(now, seed.timeZone).forEachIndexed { index, start ->
            val appointment = seed.appointment(index, start)
            if (isBookable(appointment, seed)) {
                appointmentDataSource.create(appointment)
                eventProducer.send(
                    AppointmentEvent.ClientVisitBooked(
                        appointment.businessId,
                        appointment.client.id,
                        appointment.dateEnd,
                    ),
                )
            }
        }
        DemoBookingPlan.requestStarts(now, seed.timeZone)
            .map { start -> seed.request(start) }
            .filter { isBookable(it, seed) }
            .take(SAMPLE_REQUESTS)
            .forEach { requestDataSource.create(it) }
    }

    private suspend fun isBookable(booking: AppointmentRepresentation, seed: DemoSeed): Boolean {
        val violation = runSuspendCatching {
            seed.settings.requireBookable(booking.date, booking.dateEnd, seed.employeeSchedule)
            employeeBookings.requireFree(booking, seed.settings)
        }.exceptionOrNull() ?: return true
        if (violation !is BusinessError) throw violation
        return false
    }

    private class DemoSeed(
        val business: BusinessDTO,
        val settings: AppointmentSettings,
        private val contexts: List<AppointmentBookingContext>,
    ) {
        val timeZone = business.timeZone
        val employeeSchedule = contexts.first().employee.schedule
        private val services = contexts.first().services

        fun appointment(index: Int, start: Instant): Appointment {
            val context = contexts[index % contexts.size]
            return Appointment(
                id = Uuid.random(),
                initiatorUserId = business.ownerUserId,
                businessId = business.id,
                employee = context.employeeSnapshot(),
                client = context.clientSnapshot(),
                services = context.serviceSnapshots(listOf(RequestedService(services[index % services.size].id, 1))),
                status = AppointmentStatus.SCHEDULED,
                date = start,
                note = "",
                cancellationReason = "",
                completedBy = null,
                priceAdjustment = null,
            )
        }

        fun request(start: Instant): AppointmentRequest {
            val context = contexts.last()
            return AppointmentRequest(
                id = Uuid.random(),
                initiatorUserId = business.ownerUserId,
                businessId = business.id,
                employee = context.employeeSnapshot(),
                client = context.requestClientSnapshot(business.ownerUserId),
                services = context.serviceSnapshots(listOf(RequestedService(services.first().id, 1))),
                status = AppointmentRequestStatus.PENDING,
                date = start,
                note = "",
                declineReason = "",
            )
        }
    }

    private fun Throwable.isGone(): Boolean {
        return this is BusinessError && statusCode == HttpStatusCode.NotFound.value
    }

    companion object {
        private const val SAMPLE_REQUESTS = 2
    }
}
