package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Appointment(
    @ProtoNumber(1) override val id: Uuid,
    @ProtoNumber(2) override val initiatorUserId: Uuid,
    @ProtoNumber(3) override val businessId: Uuid,
    @ProtoNumber(4) override val employee: EmployeeSnapshot,
    @ProtoNumber(5) val client: ClientSnapshot,
    @ProtoNumber(6) val services: List<ServiceSnapshot>,
    @ProtoNumber(7) val status: AppointmentStatus,
    @ProtoNumber(8) override val date: Instant,
    @ProtoNumber(9) val note: String,
    @ProtoNumber(10) val cancellationReason: String,
    @ProtoNumber(11) val completedBy: AppointmentCompletedBy?,
    @ProtoNumber(12) val priceAdjustment: PriceAdjustment?,
) : AppointmentRepresentation {
    @Transient
    override val dateEnd = date + services.fold(0.minutes) { acc, service ->
        acc + service.duration
    }

    @Transient
    val totalAmount: Money = services.map { it.price }.reduce { acc, price -> acc + price }

    fun hasStarted(now: Instant = Clock.System.now()): Boolean = date <= now

    fun requireEditable(now: Instant = Clock.System.now()) {
        when (status) {
            AppointmentStatus.CANCELLED -> throw AppointmentStatusError.AlreadyCancelled()
            AppointmentStatus.UNRESOLVED -> throw AppointmentStatusError.EditWindowClosed()
            AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW -> Unit
        }
        requireWithinEditWindow(now)
    }

    fun requireCompletable(now: Instant = Clock.System.now()) {
        requireEditable(now)
        if (status != AppointmentStatus.COMPLETED) requireStarted(now)
    }

    fun requireNoShowMarkable(now: Instant = Clock.System.now()) {
        requireEditable(now)
        if (status != AppointmentStatus.NO_SHOW) requireStarted(now)
    }

    private fun requireWithinEditWindow(now: Instant) {
        if (now > dateEnd + EDIT_WINDOW) throw AppointmentStatusError.EditWindowClosed()
    }

    private fun requireStarted(now: Instant) {
        if (!hasStarted(now)) throw AppointmentStatusError.NotStarted()
    }

    companion object {
        val EDIT_WINDOW = 7.days

        fun stub(
            id: Uuid = Uuid.random(),
            initiatorUserId: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            date: Instant = Instant.fromEpochMilliseconds(0),
        ) = Appointment(
            id = id,
            initiatorUserId = initiatorUserId,
            businessId = businessId,
            employee = EmployeeSnapshot.stub(),
            client = ClientSnapshot.stub(userId = initiatorUserId),
            services = listOf(ServiceSnapshot.stub()),
            status = AppointmentStatus.SCHEDULED,
            date = date,
            note = "Note",
            cancellationReason = "",
            completedBy = null,
            priceAdjustment = null,
        )
    }
}
