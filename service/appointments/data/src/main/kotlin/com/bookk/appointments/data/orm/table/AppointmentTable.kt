package com.bookk.appointments.data.orm.table

import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

private const val BLIND_INDEX_LENGTH = 44

object AppointmentTable : BaseUUIDTable("appointment") {
    val initiatorUserId = uuid("initiator_user_id")
    val businessId = reference("business_id", AppointmentBusinessTable.id, onDelete = ReferenceOption.CASCADE)
    val employeeId = uuid("employee_id")
    val employeeUserId = uuid("employee_user_id").index()
    val clientId = uuid("client_id")
    val clientUserId = uuid("client_user_id").nullable()
    val dateStart = timestamp("date_start")
    val dateEnd = timestamp("date_end").index()
    val status = enumeration("status", AppointmentStatus::class).index()
    val cancellationReason = varchar("cancellation_reason", 2048)
    val completedBy = enumeration("completed_by", AppointmentCompletedBy::class).nullable()
    val employeeNameEncrypted = binary("employee_name_encrypted", 4128).nullable()
    val clientNameEncrypted = binary("client_name_encrypted", 4128).nullable()
    val clientPhoneEncrypted = binary("client_phone_encrypted", 128).nullable()
    val clientEmailEncrypted = binary("client_email_encrypted", 2080).nullable()
    val noteEncrypted = binary("note_encrypted", 8224).nullable()
    val clientNoteEncrypted = binary("client_note_encrypted", 8224).nullable()
    val clientNameIndex = char("client_name_index", BLIND_INDEX_LENGTH).nullable()

    init {
        index(false, initiatorUserId, businessId, dateStart)
        index(false, employeeId, dateEnd)
        index("appointment_business_client_name_index", false, businessId, clientNameIndex)
        index("appointment_business_id_date_start", false, businessId, dateStart)
        index("appointment_employee_id_date_start", false, employeeId, dateStart)
        index("appointment_client_id_date_start", false, clientId, dateStart)
        index("appointment_client_user_id_date_start", false, clientUserId, dateStart)
    }
}
