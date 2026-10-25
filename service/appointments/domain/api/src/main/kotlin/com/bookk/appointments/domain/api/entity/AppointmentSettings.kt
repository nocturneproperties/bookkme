package com.bookk.appointments.domain.api.entity

import com.bookk.core.containedIn
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
    @ProtoNumber(9) val automaticCompletion: Boolean
) {
    companion object {
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
        automaticCompletion = true
    )

    fun isInWorkday(date: Instant): Boolean {
        val localDate = date.toLocalDateTime(timeZone)
        if (!schedule[localDate.dayOfWeek].isActive) return false
        return schedule.dayOffs.none { localDate.date in it.start..it.end }
    }

    fun isInWorktime(date: Instant, dateEnd: Instant): Boolean {
        val start = date.toLocalDateTime(timeZone)
        val startTime = start.time.toMillisecondOfDay()
        val endTime = dateEnd.toLocalDateTime(timeZone).time.toMillisecondOfDay()
        val dayOfWeek = start.dayOfWeek
        val schedule = schedule[dayOfWeek]
        return schedule.workingTime.any { time ->
            (startTime..endTime).containedIn(time.from.toMillisecondOfDay()..time.to.toMillisecondOfDay())
        }
    }
}
