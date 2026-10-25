package library.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Schedule(
    @ProtoNumber(1) val days: Map<DayOfWeek, DayOfWeekSchedule>,
    @ProtoNumber(2) val dayOffs: List<DayOffRange> = emptyList()
) {

    init {
        require(days.keys.containsAll(DayOfWeek.entries)) { "Schedule must cover all 7 days" }
    }

    constructor() : this(
        days = DayOfWeek.entries.associateWith { DayOfWeekSchedule.default(it) }
    )

    constructor(
        workingDays: List<DayOfWeek>,
        workingHours: Map<DayOfWeek, List<WorkHour>>,
        dayOffs: List<DayOffRange> = emptyList()
    ) : this(
        days = DayOfWeek.entries.associateWith { day ->
            DayOfWeekSchedule(
                workingTime = workingHours[day].orEmpty(),
                isActive = workingDays.contains(day)
            )
        },
        dayOffs = dayOffs
    )

    operator fun get(dayOfWeek: DayOfWeek): DayOfWeekSchedule = days.getValue(dayOfWeek)

    fun activeDays(): List<DayOfWeek> = days.filterValues { it.isActive }.keys.toList()

    fun workingHours(): Map<DayOfWeek, List<WorkHour>> = days.mapValues { it.value.workingTime }

    companion object {
        fun empty(): Schedule = Schedule(workingDays = emptyList(), workingHours = emptyMap())
    }
}
