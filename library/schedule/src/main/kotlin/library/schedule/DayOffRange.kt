package library.schedule

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DayOffRange(
    @ProtoNumber(1) val start: LocalDate,
    @ProtoNumber(2) val end: LocalDate
)
