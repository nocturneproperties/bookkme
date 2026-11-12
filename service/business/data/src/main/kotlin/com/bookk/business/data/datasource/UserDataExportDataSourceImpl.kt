package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.BusinessEntity
import com.bookk.business.data.orm.entity.EmployeeEntity
import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.ClientRecordExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.DayOffExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.EmploymentExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.OwnedBusinessExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.PermissionExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.WorkingHoursExport
import com.bookk.business.domain.datasource.UserDataExportDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImpl : DataSource(), UserDataExportDataSource {

    override suspend fun export(userId: Uuid): BusinessUserDataExport = dbQuery {
        val employees = EmployeeEntity.find { EmployeeTable.userId eq userId }
            .with(
                EmployeeEntity::services,
                EmployeeEntity::workingHours,
                EmployeeEntity::dayOffs,
                EmployeeEntity::grants,
            )
            .map { it.toDomain() }
        val clientRows = ClientTable.selectAll().where { ClientTable.userId eq userId }.toList()
        val businessNames =
            businessNames(employees.map { it.businessId } + clientRows.map { it[ClientTable.businessId].value })
        BusinessUserDataExport(
            ownedBusiness = BusinessEntity.find { BusinessTable.userId eq userId }.singleOrNull()?.toExport(),
            employments = employees.map { it.toExport(businessNames.getValue(it.businessId)) },
            clientRecords = clientRows.map {
                it.toClientRecord(businessNames.getValue(it[ClientTable.businessId].value))
            },
            dashboardBusinessId = BusinessDashboardTable.select(BusinessDashboardTable.businessId)
                .where { BusinessDashboardTable.userId eq userId }
                .singleOrNull()
                ?.get(BusinessDashboardTable.businessId)
                ?.value,
        )
    }

    private fun businessNames(businessIds: List<Uuid>): Map<Uuid, String> = BusinessTable
        .select(BusinessTable.id, BusinessTable.name)
        .where { BusinessTable.id inList businessIds.distinct() }
        .associate { it[BusinessTable.id].value to it[BusinessTable.name] }

    private fun BusinessEntity.toExport() = OwnedBusinessExport(
        id = id.value,
        name = name,
        description = description,
        address = address,
        latitude = latitude,
        longitude = longitude,
        currency = currency,
        timeZone = timezone,
        phone = phone,
        instagram = instagram,
        telegram = telegram,
        viber = viber,
        whatsapp = whatsapp,
        clientDataRetentionMonths = clientDataRetentionMonths,
        createdAt = createdAt,
    )

    private fun Employee.toExport(businessName: String) = EmploymentExport(
        businessId = businessId,
        businessName = businessName,
        name = name,
        lastName = lastName,
        phone = phone,
        email = email,
        suspendedAt = suspendedAt,
        permissions = BusinessResource.entries.map { resource ->
            val granted = permissions[resource]
            PermissionExport(resource.name, view = granted.view, update = granted.update, delete = granted.delete)
        },
        workingHours = schedule.activeDays().sorted().flatMap { day ->
            schedule[day].workingTime.map { WorkingHoursExport(day.name, start = it.from, end = it.to) }
        },
        daysOff = schedule.dayOffs.map { DayOffExport(start = it.start, end = it.end) },
    )

    private fun ResultRow.toClientRecord(businessName: String) = ClientRecordExport(
        businessId = this[ClientTable.businessId].value,
        businessName = businessName,
        name = this[ClientTable.name],
        lastName = this[ClientTable.lastName],
        phone = this[ClientTable.phone],
        email = this[ClientTable.email],
        description = this[ClientTable.description],
        lastActivityAt = this[ClientTable.lastActivityAt],
    )
}
