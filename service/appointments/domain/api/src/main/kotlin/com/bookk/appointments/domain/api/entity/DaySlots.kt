package com.bookk.appointments.domain.api.entity

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DaySlots(
    @ProtoNumber(1) val date: LocalDate,
    @ProtoNumber(2) val slots: List<TimeSlot>,
) {
    companion object {
        fun stub(
            date: LocalDate = LocalDate(1970, 1, 1),
            slots: List<TimeSlot> = emptyList(),
        ): DaySlots {
            return DaySlots(date = date, slots = slots)
        }
    }
}
