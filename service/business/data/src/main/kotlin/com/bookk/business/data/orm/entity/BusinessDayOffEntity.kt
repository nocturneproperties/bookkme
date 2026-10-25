package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.core.data.DecoratorUuidEntityClass
import library.schedule.DayOffRange
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.uuid.Uuid

internal class BusinessDayOffEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var business by BusinessEntity referencedOn BusinessDayOffTable.businessId
    var startDate by BusinessDayOffTable.startDate
    var endDate by BusinessDayOffTable.endDate

    fun domain(): DayOffRange = DayOffRange(start = startDate, end = endDate)

    companion object : DecoratorUuidEntityClass<BusinessDayOffEntity>(BusinessDayOffTable) {
        fun batchReplace(businessId: Uuid, ranges: List<DayOffRange>) {
            BusinessDayOffTable.deleteWhere { BusinessDayOffTable.businessId eq businessId }
            BusinessDayOffTable.batchInsert(ranges) {
                this[BusinessDayOffTable.businessId] = businessId
                this[BusinessDayOffTable.startDate] = it.start
                this[BusinessDayOffTable.endDate] = it.end
            }
        }
    }
}
