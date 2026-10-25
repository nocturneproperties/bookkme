package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.EmployeeDayOffTable
import com.bookk.core.data.DecoratorUuidEntityClass
import library.schedule.DayOffRange
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.uuid.Uuid

internal class EmployeeDayOffEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var employee by EmployeeEntity referencedOn EmployeeDayOffTable.employeeId
    var startDate by EmployeeDayOffTable.startDate
    var endDate by EmployeeDayOffTable.endDate

    fun domain(): DayOffRange = DayOffRange(start = startDate, end = endDate)

    companion object : DecoratorUuidEntityClass<EmployeeDayOffEntity>(EmployeeDayOffTable) {
        fun batchReplace(employeeId: Uuid, ranges: List<DayOffRange>) {
            EmployeeDayOffTable.deleteWhere { EmployeeDayOffTable.employeeId eq employeeId }
            EmployeeDayOffTable.batchInsert(ranges) {
                this[EmployeeDayOffTable.employeeId] = employeeId
                this[EmployeeDayOffTable.startDate] = it.start
                this[EmployeeDayOffTable.endDate] = it.end
            }
        }
    }
}
