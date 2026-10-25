package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.core.data.DecoratorUuidEntityClass
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import library.schedule.WorkHour
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.uuid.Uuid

internal class BusinessWorkingHourEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var business by BusinessEntity referencedOn BusinessWorkingHoursTable.businessId
    var dayOfWeek by BusinessWorkingHoursTable.dayOfWeek
    var startTime by BusinessWorkingHoursTable.startTime
    var endTime by BusinessWorkingHoursTable.endTime

    fun domain(): WorkHour = WorkHour(from = startTime, to = endTime)

    companion object : DecoratorUuidEntityClass<BusinessWorkingHourEntity>(BusinessWorkingHoursTable) {
        fun batchReplace(businessId: Uuid, hours: Map<DayOfWeek, List<WorkHour>>) {
            BusinessWorkingHoursTable.deleteWhere { BusinessWorkingHoursTable.businessId eq businessId }
            val rows = hours.flatMap { (dayOfWeek, workingTime) -> workingTime.map { dayOfWeek to it } }
            BusinessWorkingHoursTable.batchInsert(rows) { (dayOfWeek, workHour) ->
                this[BusinessWorkingHoursTable.businessId] = businessId
                this[BusinessWorkingHoursTable.dayOfWeek] = dayOfWeek.isoDayNumber.toByte()
                this[BusinessWorkingHoursTable.startTime] = workHour.from
                this[BusinessWorkingHoursTable.endTime] = workHour.to
            }
        }
    }
}

internal fun Iterable<BusinessWorkingHourEntity>.toWorkingHours(): Map<DayOfWeek, List<WorkHour>> {
    return this
        .groupBy { DayOfWeek(it.dayOfWeek.toInt()) }
        .mapValues { (_, rows) -> rows.map { it.domain() } }
}
