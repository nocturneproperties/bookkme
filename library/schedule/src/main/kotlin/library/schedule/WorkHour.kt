package library.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class WorkHour(
    @ProtoNumber(1) val from: LocalTime,
    @ProtoNumber(2) val to: LocalTime,
) {
    val endsNextDay: Boolean get() = to < from
    val isEmpty: Boolean get() = to == from

    fun slotOn(date: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val endDate = if (endsNextDay) date.plus(1, DateTimeUnit.DAY) else date
        return date.atTime(from).toInstant(timeZone)..endDate.atTime(to).toInstant(timeZone)
    }

    companion object {
        val NINE_TO_FIVE = WorkHour(
            from = LocalTime(9, 0),
            to = LocalTime(17, 0),
        )
    }
}
