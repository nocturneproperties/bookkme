package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.experimental.or

object AppointmentBusinessTable: BaseUUIDTable("business_has_appointments") {
    val enabled = bool("enabled")
    val name = varchar("name", 512)
    val address = varchar("address", 512)
    val timeZone = varchar("time_zone", 256)
    val workingDays = byte("working_days").default(MONDAY_TO_FRIDAY)

    val sourceUpdatedAt = timestamp("source_updated_at").nullable()
}

private val MONDAY_TO_FRIDAY: Byte = DayOfWeek.entries
    .filter { it < DayOfWeek.SATURDAY }
    .fold(0) { acc, day -> acc or (1 shl day.isoDayNumber).toByte() }
