package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.core.data.DecoratorUuidEntityClass
import library.schedule.DayOffRange
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.uuid.Uuid

internal class DayOffEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var business by AppointmentBusinessEntity referencedOn DayOffsTable.businessId
    var startDate by DayOffsTable.startDate
    var endDate by DayOffsTable.endDate

    fun domain(): DayOffRange = DayOffRange(start = startDate, end = endDate)

    companion object : DecoratorUuidEntityClass<DayOffEntity>(DayOffsTable) {
        fun batchReplace(businessId: Uuid, ranges: List<DayOffRange>) {
            DayOffsTable.deleteWhere { DayOffsTable.businessId eq businessId }
            DayOffsTable.batchInsert(ranges) {
                this[DayOffsTable.businessId] = businessId
                this[DayOffsTable.startDate] = it.start
                this[DayOffsTable.endDate] = it.end
            }
        }
    }
}