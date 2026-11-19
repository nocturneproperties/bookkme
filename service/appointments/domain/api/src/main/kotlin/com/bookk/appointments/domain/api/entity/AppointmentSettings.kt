package com.bookk.appointments.domain.api.entity

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.permissions.ResourcePermission
import library.schedule.Schedule
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AppointmentSettings(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val timeZone: TimeZone,
    @ProtoNumber(4) val schedule: Schedule,
    @ProtoNumber(5) val automaticApproval: Boolean,
    @ProtoNumber(6) val inBetweenBreakInMinutes: Int,
    @ProtoNumber(7) val appointmentNote: String,
    @ProtoNumber(8) val permissions: ResourcePermission,
    @ProtoNumber(9) val automaticCompletion: Boolean,
    @ProtoNumber(10) val maxPendingRequestsPerClient: Int = DEFAULT_MAX_PENDING_REQUESTS_PER_CLIENT,
) {
    companion object {
        const val DEFAULT_MAX_PENDING_REQUESTS_PER_CLIENT = 10

        fun stub(businessId: Uuid = Uuid.random()) = AppointmentSettings(businessId = businessId, TimeZone.of("UTC"))
    }

    constructor(businessId: Uuid, timeZone: TimeZone) : this(
        id = Uuid.random(),
        businessId = businessId,
        timeZone = timeZone,
        schedule = Schedule(),
        automaticApproval = false,
        inBetweenBreakInMinutes = 10,
        appointmentNote = "",
        permissions = ResourcePermission.NONE,
        automaticCompletion = true,
    )

    fun requireBookable(date: Instant, dateEnd: Instant, employeeSchedule: Schedule?) {
        if (!schedule.worksOn(date)) throw BookingScheduleError.BusinessDateNotAllowed()
        if (!schedule.covers(date, dateEnd, timeZone)) throw BookingScheduleError.BusinessTimeNotAllowed()
        if (employeeSchedule == null) return
        if (!employeeSchedule.worksOn(date)) throw BookingScheduleError.EmployeeDateNotAllowed()
        if (!employeeSchedule.covers(date, dateEnd, timeZone)) throw BookingScheduleError.EmployeeTimeNotAllowed()
    }

    private fun Schedule.worksOn(date: Instant): Boolean {
        return isWorkday(date.toLocalDateTime(timeZone).date) || isWorkingAt(date, timeZone)
    }
}
