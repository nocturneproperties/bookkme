package com.bookk.appointments.data.orm.table

import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object AppointmentRequestTable : BaseUUIDTable("appointment_request") {
    val userId = uuid("user_id")
    val businessId = reference("business_id", AppointmentBusinessTable.id, onDelete = ReferenceOption.CASCADE)
    val employeeId = uuid("employee_id")
    val employeeUserId = uuid("employee_user_id")
    val employeeName = varchar("employee_name", 1024)
    val clientId = uuid("client_id").index()
    val clientName = varchar("client_name", 1024)
    val clientPhone = varchar("client_phone", 24).nullable()
    val clientEmail = varchar("client_email", 512).nullable()
    val dateStart = timestamp("date_start")
    val dateEnd = timestamp("date_end")
    val note = varchar("note",2048)
    val status = enumeration("status", AppointmentRequestStatus::class)
    val declineReason = varchar("decline_reason", 2048)

    init {
        index(false, userId, businessId, dateStart)
    }
}