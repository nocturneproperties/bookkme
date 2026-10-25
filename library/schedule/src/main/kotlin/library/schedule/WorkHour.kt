package library.schedule

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class WorkHour(
    @ProtoNumber(1) val from: LocalTime,
    @ProtoNumber(2) val to: LocalTime
) {
    companion object {
        val NINE_TO_FIVE = WorkHour(
            from = LocalTime(9, 0),
            to = LocalTime(17, 0)
        )
    }
}
