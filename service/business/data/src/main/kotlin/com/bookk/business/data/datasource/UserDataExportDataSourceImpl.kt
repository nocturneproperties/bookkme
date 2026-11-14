package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.BusinessEntity
import com.bookk.business.data.orm.entity.ClientEntity
import com.bookk.business.data.orm.entity.Contact
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImpl(
    private val contactCipher: ContactCipher,
) : DataSource(), UserDataExportDataSource {

    override suspend fun export(userId: Uuid): BusinessUserDataExport = dbQuery {
        val employees = EmployeeEntity.find { EmployeeTable.userId eq userId }
            .with(
                EmployeeEntity::services,
                EmployeeEntity::workingHours,
                EmployeeEntity::dayOffs,
                EmployeeEntity::grants,
            )
            .toList()
        val employeeReader = contactCipher.reader(listOf(userId), EMPLOYEE_SOURCE)
        val clients = ClientEntity.find { ClientTable.userId eq userId }.toList()
        val clientReader = contactCipher.reader(clients.map { it.id.value }, CLIENT_SOURCE)
        val businessNames =
            businessNames(employees.map { it.businessId.value } + clients.map { it.businessId.value })
        BusinessUserDataExport(
            ownedBusiness = BusinessEntity.find { BusinessTable.userId eq userId }.singleOrNull()?.toExport(),
            employments = employees.map { entity ->
                entity.toDomain(
                    entity.contact(employeeReader),
                ).toExport(businessNames.getValue(entity.businessId.value))
            },
            clientRecords = clients.map { entity ->
                entity.toClientRecord(entity.contact(clientReader), businessNames.getValue(entity.businessId.value))
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

    private fun ClientEntity.toClientRecord(contact: Contact, businessName: String): ClientRecordExport {
        return ClientRecordExport(
            businessId = businessId.value,
            businessName = businessName,
            name = contact.name,
            lastName = contact.lastName,
            phone = contact.phone,
            email = contact.email,
            description = contact.description,
            lastActivityAt = lastActivityAt,
        )
    }

    private companion object {
        const val CLIENT_SOURCE = "client"
        const val EMPLOYEE_SOURCE = "employee"
    }
}
