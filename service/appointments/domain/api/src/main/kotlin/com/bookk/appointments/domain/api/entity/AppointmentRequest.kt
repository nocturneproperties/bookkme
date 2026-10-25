package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AppointmentRequest(
    @ProtoNumber(1) override val id: Uuid,
    @ProtoNumber(2) override val userId: Uuid,
    @ProtoNumber(3) override val businessId: Uuid,
    @ProtoNumber(4) override val employee: EmployeeSnapshot,
    @ProtoNumber(5) val client: ClientSnapshot,
    @ProtoNumber(6) val services: List<ServiceSnapshot>,
    @ProtoNumber(7) val status: AppointmentRequestStatus,
    @ProtoNumber(8) override val date: Instant,
    @ProtoNumber(9) val note: String,
    @ProtoNumber(10) val declineReason: String,
) : AppointmentRepresentation {

    @Transient
    override val dateEnd = date + services.fold(0.minutes) { acc, service ->
        acc + service.duration
    }

    @Transient
    val totalAmount = services.map { it.price }.reduce { acc, price -> acc + price }

    fun requirePending() {
        when (status) {
            AppointmentRequestStatus.PENDING -> Unit
            AppointmentRequestStatus.APPROVED -> throw AppointmentRequestStatusError.AlreadyApproved()
            AppointmentRequestStatus.DECLINED,
            AppointmentRequestStatus.CANCELLED -> throw AppointmentRequestStatusError.AlreadyDeclined()
        }
    }

    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            date: Instant = Instant.fromEpochMilliseconds(0),
            employee: EmployeeSnapshot = EmployeeSnapshot.stub()
        ) = AppointmentRequest(
            id = id,
            userId = userId,
            businessId = businessId,
            employee = employee,
            client = ClientSnapshot.stub(),
            services = listOf(ServiceSnapshot.stub()),
            status = AppointmentRequestStatus.PENDING,
            date = date,
            note = "Note",
            declineReason = ""
        )
    }
}