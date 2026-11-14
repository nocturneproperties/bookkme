package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.Contact
import com.bookk.business.data.orm.entity.EmployeeEntity
import com.bookk.business.data.orm.entity.EncryptedContact
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.EmployeeCanProvideServiceTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeDataSourceImpl(private val contactCipher: ContactCipher) : DataSource(), EmployeeDataSource {

    override suspend fun createEmployee(employee: Employee): Employee = dbQuery {
        val contact = Contact(
            name = employee.name.trim(),
            lastName = employee.lastName.trim(),
            phone = employee.phone?.trim(),
            email = employee.email?.trim(),
        )
        EmployeeEntity.new(employee, encryptExisting(employee.userId, contact)).toDomain(contact)
    }

    override suspend fun updateEmployee(model: EmployeeUpdateModel): Employee = dbQuery {
        val entity = EmployeeEntity.find { EmployeeTable.id eq model.id }.forUpdate().singleOrNull()
            ?: throw Error.NotFound()
        val contact = Contact(
            name = model.name.trim(),
            lastName = model.lastName.trim(),
            phone = model.phone?.trim(),
            email = model.email?.trim(),
        )
        entity.update(model, encryptExisting(entity.userId, contact))
        entity.toDomain(contact)
    }

    override suspend fun setSuspendedAt(id: Uuid, suspendedAt: Instant?): Employee = dbQuery {
        (EmployeeEntity.findByIdAndUpdateSuspension(id, suspendedAt) ?: throw Error.NotFound()).domain()
    }

    override suspend fun getEmployees(businessId: Uuid): List<Employee> = dbQuery {
        EmployeeEntity.find {
            EmployeeTable.businessId eq businessId
        }
            .toList()
            .with(
                EmployeeEntity::services,
                EmployeeEntity::workingHours,
                EmployeeEntity::dayOffs,
                EmployeeEntity::grants,
            )
            .domains()
    }

    override suspend fun getEmployee(businessId: Uuid, id: Uuid): Employee? = dbQuery {
        EmployeeEntity.find {
            (EmployeeTable.businessId eq businessId) and (EmployeeTable.id eq id)
        }
            .firstOrNull()
            ?.domain()
    }

    override suspend fun getEmployeeByUserId(businessId: Uuid, userId: Uuid): Employee? = dbQuery {
        EmployeeEntity.find {
            (EmployeeTable.businessId eq businessId) and (EmployeeTable.userId eq userId)
        }
            .firstOrNull()
            ?.domain()
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
        updatedAt: Instant,
    ): Int = dbQuery {
        val employments = EmployeeEntity.find {
            (EmployeeTable.userId eq userId) and
                (EmployeeTable.sourceUpdatedAt.isNull() or (EmployeeTable.sourceUpdatedAt less updatedAt))
        }.forUpdate().toList()
        val reader = contactCipher.reader(listOf(userId), SOURCE)
        employments.count { entity ->
            val current = entity.contact(reader)
            val synced = current.copy(
                name = name.trim(),
                lastName = lastName.trim(),
                email = email.trim(),
                phone = phone?.trim() ?: current.phone,
            )
            val encrypted = contactCipher.encrypt(userId, synced) ?: return@count false
            entity.store(encrypted)
            entity.sourceUpdatedAt = updatedAt
            entity.updatedAt = Clock.System.now()
            true
        }
    }

    override suspend fun getServiceIds(employeeId: Uuid): List<Uuid> = dbQuery {
        EmployeeCanProvideServiceTable
            .select(EmployeeCanProvideServiceTable.serviceId)
            .where { EmployeeCanProvideServiceTable.employeeId eq employeeId }
            .map { it[EmployeeCanProvideServiceTable.serviceId].value }
    }

    override suspend fun getBusinessIdsOfUserForUpdate(userId: Uuid): List<Uuid> = dbQuery {
        EmployeeTable.select(EmployeeTable.businessId)
            .where { EmployeeTable.userId eq userId }
            .forUpdate()
            .map { it[EmployeeTable.businessId].value }
            .distinct()
    }

    override suspend fun deleteEmployeesByUserId(userId: Uuid) = dbQuery<Unit> {
        EmployeeTable.deleteWhere { EmployeeTable.userId eq userId }
    }

    override suspend fun getEmployeesByService(serviceId: Uuid): List<Employee> = dbQuery {
        val employeeIds = EmployeeCanProvideServiceTable
            .select(EmployeeCanProvideServiceTable.employeeId)
            .where { EmployeeCanProvideServiceTable.serviceId eq serviceId }

        EmployeeEntity.find {
            EmployeeTable.id inSubQuery employeeIds
        }
            .toList()
            .with(
                EmployeeEntity::services,
                EmployeeEntity::workingHours,
                EmployeeEntity::dayOffs,
                EmployeeEntity::grants,
            )
            .domains()
    }

    override suspend fun getOwnedBusinessMemberUserIds(ownerUserId: Uuid): List<Uuid> = dbQuery {
        EmployeeTable.innerJoin(BusinessTable)
            .select(EmployeeTable.userId)
            .where { (BusinessTable.userId eq ownerUserId) and (EmployeeTable.userId neq ownerUserId) }
            .map { it[EmployeeTable.userId] }
    }

    override suspend fun encryptPlaintextEmployees(limit: Int): Int = dbQuery {
        val plaintext = EmployeeEntity.find { EmployeeTable.nameEncrypted.isNull() }.limit(limit).forUpdate().toList()
        plaintext.forEach { entity -> entity.store(encryptExisting(entity.userId, entity.plaintextContact())) }
        plaintext.size
    }

    private suspend fun encryptExisting(userId: Uuid, contact: Contact): EncryptedContact {
        return checkNotNull(contactCipher.encrypt(userId, contact)) { "The employee user $userId has a shredded key" }
    }

    private suspend fun EmployeeEntity.domain(): Employee {
        return toDomain(contact(contactCipher.reader(listOf(userId), SOURCE)))
    }

    private suspend fun List<EmployeeEntity>.domains(): List<Employee> {
        val reader = contactCipher.reader(map { it.userId }, SOURCE)
        return map { it.toDomain(it.contact(reader)) }
    }

    private companion object {
        const val SOURCE = "employee"
    }
}
