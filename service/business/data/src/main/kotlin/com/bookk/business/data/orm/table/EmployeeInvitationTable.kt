package com.bookk.business.data.orm.table

import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object EmployeeInvitationTable : BaseUUIDTable("employee_invitation") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE)
    val invitedBy = uuid("invited_by")
    val codeHash = varchar("code_hash", 64).uniqueIndex().nullable()
    val status = enumeration("status", EmployeeInvitationStatus::class)

    init {
        index(false, businessId, createdAt)
    }
}
