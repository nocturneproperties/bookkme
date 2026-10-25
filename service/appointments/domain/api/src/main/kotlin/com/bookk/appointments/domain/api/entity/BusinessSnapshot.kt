package com.bookk.appointments.domain.api.entity

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.uuid.Uuid

@Serializable
data class BusinessSnapshot(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val address: String,
    @ProtoNumber(4) val timeZone: TimeZone,
    @ProtoNumber(5) val isEnabled: Boolean,
    @ProtoNumber(6) val schedule: Schedule
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Business name",
            address: String = "Business address",
            timeZone: TimeZone = TimeZone.UTC,
            isEnabled: Boolean = true,
            schedule: Schedule = Schedule()
        ) = BusinessSnapshot(
            id = id,
            name = name,
            address = address,
            timeZone = timeZone,
            isEnabled = isEnabled,
            schedule = schedule
        )
    }
}
