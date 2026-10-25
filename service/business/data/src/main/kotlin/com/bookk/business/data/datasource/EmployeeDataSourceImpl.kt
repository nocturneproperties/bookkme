package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.EmployeeEntity
import com.bookk.business.data.orm.table.EmployeeCanProvideServiceTable
import com.bookk.business.data.orm.table.EmployeeDayOffTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.data.orm.table.EmployeeWorkingHoursTable
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeDataSourceImpl : DataSource(), EmployeeDataSource {

    override suspend fun createEmployee(employee: Employee): Employee = dbQuery {
        EmployeeEntity.new(employee).toDomain()
    }

    override suspend fun updateEmployee(model: EmployeeUpdateModel): Employee = dbQuery {
        (EmployeeEntity.findByIdAndUpdate(model) ?: throw Error.NotFound()).toDomain()
    }

    override suspend fun setSuspendedAt(id: Uuid, suspendedAt: Instant?): Employee = dbQuery {
        (EmployeeEntity.findByIdAndUpdateSuspension(id, suspendedAt) ?: throw Error.NotFound()).toDomain()
    }

    override suspend fun getEmployees(businessId: Uuid): List<Employee> = dbQuery {
        EmployeeEntity.find {
            EmployeeTable.businessId eq businessId
        }
            .toList()
            .with(EmployeeEntity::services, EmployeeEntity::workingHours, EmployeeEntity::dayOffs, EmployeeEntity::grants)
            .map(EmployeeEntity::toDomain)
    }

    override suspend fun getEmployee(businessId: Uuid, id: Uuid): Employee? = dbQuery {
        EmployeeEntity.find {
            (EmployeeTable.businessId eq businessId) and (EmployeeTable.id eq id)
        }
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun getEmployeeByUserId(businessId: Uuid, userId: Uuid): Employee? = dbQuery {
        EmployeeEntity.find {
            (EmployeeTable.businessId eq businessId) and (EmployeeTable.userId eq userId)
        }
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun deleteEmployee(businessId: Uuid, id: Uuid): Boolean = dbQuery {
        EmployeeTable.deleteWhere {
            (EmployeeTable.businessId eq businessId) and (EmployeeTable.id eq id)
        } != 0
    }

    override suspend fun updateIntegratedEmployees(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant
    ): Int = dbQuery {
        EmployeeTable.update(
            where = {
                (EmployeeTable.userId eq userId) and
                    (EmployeeTable.sourceUpdatedAt.isNull() or (EmployeeTable.sourceUpdatedAt less updatedAt))
            }
        ) {
            it[this.name] = name.trim()
            it[this.lastName] = lastName.trim()
            it[this.email] = email.trim()
            phone?.let { value -> it[this.phone] = value.trim() }
            it[EmployeeTable.sourceUpdatedAt] = updatedAt
            it[EmployeeTable.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun getServiceIds(employeeId: Uuid): List<Uuid> = dbQuery {
        EmployeeCanProvideServiceTable
            .select(EmployeeCanProvideServiceTable.serviceId)
            .where { EmployeeCanProvideServiceTable.employeeId eq employeeId }
            .map { it[EmployeeCanProvideServiceTable.serviceId].value }
    }

    override suspend fun anonymizeEmployeesByUserId(userId: Uuid): Int = dbQuery {
        val employeeIds = EmployeeTable.select(EmployeeTable.id)
            .where { EmployeeTable.userId eq userId }
            .map { it[EmployeeTable.id].value }

        if (employeeIds.isEmpty()) return@dbQuery 0

        EmployeeCanProvideServiceTable.deleteWhere { EmployeeCanProvideServiceTable.employeeId inList employeeIds }
        EmployeeWorkingHoursTable.deleteWhere { EmployeeWorkingHoursTable.employeeId inList employeeIds }
        EmployeeDayOffTable.deleteWhere { EmployeeDayOffTable.employeeId inList employeeIds }

        EmployeeTable.update(where = { EmployeeTable.userId eq userId }) {
            it[name] = "Deleted User"
            it[lastName] = ""
            it[phone] = null
            it[email] = null
            it[workingDays] = 0
            it[EmployeeTable.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun getEmployeesByService(serviceId: Uuid): List<Employee> = dbQuery {
        val employeeIds = EmployeeCanProvideServiceTable
            .select(EmployeeCanProvideServiceTable.employeeId)
            .where { EmployeeCanProvideServiceTable.serviceId eq serviceId }

        EmployeeEntity.find {
            EmployeeTable.id inSubQuery employeeIds
        }
            .toList()
            .with(EmployeeEntity::services, EmployeeEntity::workingHours, EmployeeEntity::dayOffs, EmployeeEntity::grants)
            .map(EmployeeEntity::toDomain)
    }
}
