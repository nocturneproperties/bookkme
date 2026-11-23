package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class BookedRange(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val start: Instant,
    @ProtoNumber(3) val end: Instant,
) {
    fun blocks(start: Instant, end: Instant, gap: Duration): Boolean {
        return start < this.end + gap && end > this.start - gap
    }

    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            start: Instant = Instant.fromEpochMilliseconds(0),
            end: Instant = Instant.fromEpochMilliseconds(0),
        ): BookedRange {
            return BookedRange(id = id, start = start, end = end)
        }
    }
}
