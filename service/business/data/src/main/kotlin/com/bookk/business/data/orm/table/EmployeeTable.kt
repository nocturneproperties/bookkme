package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object EmployeeTable : BaseUUIDTable("employee") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val name = varchar("name", 512)
    val lastName = varchar("lastname", 512)
    val phone = varchar("phone", 512).nullable().index()
    val email = varchar("email", 512).nullable().index()
    val userId = uuid("user_id").index()
    val sourceUpdatedAt = timestamp("source_updated_at").nullable()
    val workingDays = byte("working_days").default(0)
    val suspendedAt = timestamp("suspended_at").nullable()

    init {
        uniqueIndex(businessId, userId)
    }
}
