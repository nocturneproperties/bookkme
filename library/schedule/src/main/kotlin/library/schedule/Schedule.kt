package library.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class Schedule(
    @ProtoNumber(1) val days: Map<DayOfWeek, DayOfWeekSchedule>,
    @ProtoNumber(2) val dayOffs: List<DayOffRange> = emptyList(),
) {

    init {
        require(days.keys.containsAll(DayOfWeek.entries)) { "Schedule must cover all 7 days" }
    }

    constructor() : this(
        days = DayOfWeek.entries.associateWith { DayOfWeekSchedule.default(it) },
    )

    constructor(
        workingDays: List<DayOfWeek>,
        workingHours: Map<DayOfWeek, List<WorkHour>>,
        dayOffs: List<DayOffRange> = emptyList(),
    ) : this(
        days = DayOfWeek.entries.associateWith { day ->
            DayOfWeekSchedule(
                workingTime = workingHours[day].orEmpty(),
                isActive = workingDays.contains(day),
            )
        },
        dayOffs = dayOffs,
    )

    operator fun get(dayOfWeek: DayOfWeek): DayOfWeekSchedule = days.getValue(dayOfWeek)

    fun activeDays(): List<DayOfWeek> = days.filterValues { it.isActive }.keys.toList()

    fun workingHours(): Map<DayOfWeek, List<WorkHour>> = days.mapValues { it.value.workingTime }

    fun hasEmptyWorkHour(): Boolean {
        return days.values.any { day -> day.workingTime.any { it.isEmpty } }
    }

    fun workingWeek(): Schedule {
        return copy(dayOffs = emptyList())
    }

    fun isWorkday(date: LocalDate): Boolean {
        if (!get(date.dayOfWeek).isActive) return false
        return dayOffs.none { date in it.start..it.end }
    }

    fun covers(start: Instant, end: Instant, timeZone: TimeZone): Boolean {
        var coveredUntil = start
        for (slot in workingSlots(start, end, timeZone)) {
            if (slot.start > coveredUntil) return false
            if (slot.endInclusive >= end) return true
            coveredUntil = maxOf(coveredUntil, slot.endInclusive)
        }
        return false
    }

    fun isWorkingAt(instant: Instant, timeZone: TimeZone): Boolean {
        return workingSlots(instant, instant, timeZone).any { instant >= it.start && instant < it.endInclusive }
    }

    private fun workingSlots(start: Instant, end: Instant, timeZone: TimeZone): List<ClosedRange<Instant>> {
        val firstDate = start.toLocalDateTime(timeZone).date.minus(1, DateTimeUnit.DAY)
        val lastDate = end.toLocalDateTime(timeZone).date
        return generateSequence(firstDate) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= lastDate }
            .filter(::isWorkday)
            .flatMap { date -> get(date.dayOfWeek).workingTime.map { it.slotOn(date, timeZone) } }
            .sortedBy { it.start }
            .toList()
    }

    companion object {
        fun empty(): Schedule = Schedule(workingDays = emptyList(), workingHours = emptyMap())
    }
}
