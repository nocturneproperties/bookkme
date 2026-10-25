package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object SettingsTable: BaseUUIDTable("appointment_settings") {
    val businessId = reference("business_id", AppointmentBusinessTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val inBetweenBreakInMinutes = integer("in_between_break_in_minutes")
    val appointmentNote = varchar("appointment_note", 2048)
    val automaticApproval = bool("automatic_approval")
    val automaticCompletion = bool("automatic_completion").default(true)
}
