package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.EmployeeWorkingHoursTable
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

internal class EmployeeWorkingHourEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var employee by EmployeeEntity referencedOn EmployeeWorkingHoursTable.employeeId
    var dayOfWeek by EmployeeWorkingHoursTable.dayOfWeek
    var startTime by EmployeeWorkingHoursTable.startTime
    var endTime by EmployeeWorkingHoursTable.endTime

    fun domain(): WorkHour = WorkHour(from = startTime, to = endTime)

    companion object : DecoratorUuidEntityClass<EmployeeWorkingHourEntity>(EmployeeWorkingHoursTable) {
        fun batchReplace(employeeId: Uuid, hours: Map<DayOfWeek, List<WorkHour>>) {
            EmployeeWorkingHoursTable.deleteWhere { EmployeeWorkingHoursTable.employeeId eq employeeId }
            val rows = hours.flatMap { (dayOfWeek, workingTime) -> workingTime.map { dayOfWeek to it } }
            EmployeeWorkingHoursTable.batchInsert(rows) { (dayOfWeek, workHour) ->
                this[EmployeeWorkingHoursTable.employeeId] = employeeId
                this[EmployeeWorkingHoursTable.dayOfWeek] = dayOfWeek.isoDayNumber.toByte()
                this[EmployeeWorkingHoursTable.startTime] = workHour.from
                this[EmployeeWorkingHoursTable.endTime] = workHour.to
            }
        }
    }
}

internal fun Iterable<EmployeeWorkingHourEntity>.toWorkingHours(): Map<DayOfWeek, List<WorkHour>> {
    return this
        .groupBy { DayOfWeek(it.dayOfWeek.toInt()) }
        .mapValues { (_, rows) -> rows.map { it.domain() } }
}
