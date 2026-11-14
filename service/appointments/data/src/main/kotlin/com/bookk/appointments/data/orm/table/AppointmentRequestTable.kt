package com.bookk.appointments.data.orm.table

import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object AppointmentRequestTable : BaseUUIDTable("appointment_request") {
    val initiatorUserId = uuid("initiator_user_id")
    val businessId = reference("business_id", AppointmentBusinessTable.id, onDelete = ReferenceOption.CASCADE)
    val employeeId = uuid("employee_id")
    val employeeUserId = uuid("employee_user_id").index()
    val employeeName = varchar("employee_name", 1024).nullable()
    val clientId = uuid("client_id").index()
    val clientUserId = uuid("client_user_id").index()
    val clientName = varchar("client_name", 1024).nullable()
    val clientPhone = varchar("client_phone", 24).nullable()
    val clientEmail = varchar("client_email", 512).nullable()
    val dateStart = timestamp("date_start")
    val dateEnd = timestamp("date_end").index()
    val note = varchar("note", 2048).nullable()
    val status = enumeration("status", AppointmentRequestStatus::class)
    val declineReason = varchar("decline_reason", 2048)
    val employeeNameEncrypted = binary("employee_name_encrypted", 4128).nullable()
    val clientNameEncrypted = binary("client_name_encrypted", 4128).nullable()
    val clientPhoneEncrypted = binary("client_phone_encrypted", 128).nullable()
    val clientEmailEncrypted = binary("client_email_encrypted", 2080).nullable()
    val noteEncrypted = binary("note_encrypted", 8224).nullable()

    init {
        index(false, initiatorUserId, businessId, dateStart)
    }
}
