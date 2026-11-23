package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class TimeSlot(
    @ProtoNumber(1) val start: Instant,
    @ProtoNumber(2) val end: Instant,
) {
    companion object {
        fun stub(
            start: Instant = Instant.fromEpochMilliseconds(0),
            end: Instant = Instant.fromEpochMilliseconds(0),
        ): TimeSlot {
            return TimeSlot(start = start, end = end)
        }
    }
}
