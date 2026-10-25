package library.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DayOfWeekSchedule(
    @ProtoNumber(1) val workingTime: List<WorkHour>,
    @ProtoNumber(2) val isActive: Boolean
) {
    companion object {
        fun default(dayOfWeek: DayOfWeek): DayOfWeekSchedule {
            return DayOfWeekSchedule(
                workingTime = listOf(WorkHour.NINE_TO_FIVE),
                isActive = dayOfWeek < DayOfWeek.SATURDAY
            )
        }
    }
}
