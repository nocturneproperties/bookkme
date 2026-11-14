package com.bookk.business.data.orm.entity

import com.bookk.business.data.datasource.ContactReader
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.EmployeeCanProvideServiceTable
import com.bookk.business.data.orm.table.EmployeeDayOffTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.data.orm.table.EmployeeWorkingHoursTable
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.data.DecoratorUuidEntityClass
import library.schedule.Schedule
import library.schedule.toWorkingDays
import library.schedule.toWorkingDaysMask
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var businessId by EmployeeTable.businessId
    var name by EmployeeTable.name
    var lastName by EmployeeTable.lastName
    var phone by EmployeeTable.phone
    var email by EmployeeTable.email
    var userId by EmployeeTable.userId
    var nameEncrypted by EmployeeTable.nameEncrypted
    var lastNameEncrypted by EmployeeTable.lastNameEncrypted
    var phoneEncrypted by EmployeeTable.phoneEncrypted
    var emailEncrypted by EmployeeTable.emailEncrypted
    var sourceUpdatedAt by EmployeeTable.sourceUpdatedAt
    var createdAt by EmployeeTable.createdAt
    var updatedAt by EmployeeTable.updatedAt
    var workingDays by EmployeeTable.workingDays
    var suspendedAt by EmployeeTable.suspendedAt

    val services by ServiceEntity via EmployeeCanProvideServiceTable
    val workingHours by EmployeeWorkingHourEntity referrersOn EmployeeWorkingHoursTable.employeeId
    val dayOffs by EmployeeDayOffEntity referrersOn EmployeeDayOffTable.employeeId
    val grants by BusinessPermissionGrantEntity referrersOn BusinessPermissionGrantsTable.employeeId

    fun plaintextContact(): Contact {
        return Contact(name = name.orEmpty(), lastName = lastName.orEmpty(), phone = phone, email = email)
    }

    fun contact(reader: ContactReader): Contact {
        val encryptedName = nameEncrypted ?: return plaintextContact()
        val encrypted = EncryptedContact(
            name = encryptedName,
            lastName = checkNotNull(lastNameEncrypted) { "Employee ${id.value} has no encrypted last name" },
            phone = phoneEncrypted,
            email = emailEncrypted,
        )
        return reader.read(userId, encrypted)
    }

    fun store(contact: EncryptedContact) {
        nameEncrypted = contact.name
        lastNameEncrypted = contact.lastName
        phoneEncrypted = contact.phone
        emailEncrypted = contact.email
        name = null
        lastName = null
        phone = null
        email = null
    }

    fun update(model: EmployeeUpdateModel, contact: EncryptedContact) {
        store(contact)
        replaceSchedule(model.schedule)
        replaceServices(model.services)
        updatedAt = Clock.System.now()
    }

    fun toDomain(contact: Contact): Employee {
        return Employee(
            id = id.value,
            businessId = businessId.value,
            name = contact.name,
            lastName = contact.lastName,
            phone = contact.phone,
            email = contact.email,
            userId = userId,
            services = services.map(ServiceEntity::toDomain),
            schedule = Schedule(
                workingDays = workingDays.toWorkingDays(),
                workingHours = workingHours.toWorkingHours(),
                dayOffs = dayOffs.map { it.domain() },
            ),
            createdAt = createdAt,
            permissions = BusinessPermissions.from(grants.associate { it.resource to it.permission() }),
            suspendedAt = suspendedAt,
        )
    }

    private fun replaceSchedule(schedule: Schedule) {
        workingDays = schedule.activeDays().toWorkingDaysMask()
        EmployeeWorkingHourEntity.batchReplace(id.value, schedule.workingHours())
        EmployeeDayOffEntity.batchReplace(id.value, schedule.dayOffs)
    }

    private fun writePermissions(permissions: BusinessPermissions) {
        BusinessPermissionGrantsTable.upsertGrants(
            employeeId = id.value,
            userId = userId,
            businessId = businessId.value,
            grants = BusinessResource.entries.associateWith(permissions::get),
        )
    }

    private fun replaceServices(services: List<Service>) {
        val entityId = id.value
        val ownerBusinessId = businessId.value
        EmployeeCanProvideServiceTable.deleteWhere { EmployeeCanProvideServiceTable.employeeId eq entityId }
        EmployeeCanProvideServiceTable.batchInsert(services) { service ->
            this[EmployeeCanProvideServiceTable.employeeId] = entityId
            this[EmployeeCanProvideServiceTable.serviceId] = service.id
            this[EmployeeCanProvideServiceTable.businessId] = ownerBusinessId
        }
    }

    companion object : DecoratorUuidEntityClass<EmployeeEntity>(EmployeeTable) {
        fun new(model: Employee, contact: EncryptedContact): EmployeeEntity = new {
            businessId = EntityID(model.businessId, BusinessTable)
            userId = model.userId
            store(contact)
        }.apply {
            replaceSchedule(model.schedule)
            replaceServices(model.services)
            writePermissions(model.permissions)
        }

        fun findByIdAndUpdateSuspension(id: Uuid, suspendedAt: Instant?): EmployeeEntity? = findByIdAndUpdate(id) {
            it.suspendedAt = suspendedAt
            it.updatedAt = Clock.System.now()
        }
    }
}
