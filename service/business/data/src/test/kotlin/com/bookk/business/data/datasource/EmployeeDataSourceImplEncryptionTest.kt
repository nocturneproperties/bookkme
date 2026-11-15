package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.EmployeeCanProvideServiceTable
import com.bookk.business.data.orm.table.EmployeeDayOffTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.data.orm.table.EmployeeWorkingHoursTable
import com.bookk.business.data.orm.table.ServiceGroupTable
import com.bookk.business.data.orm.table.ServiceTable
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import library.encryption.Decrypted
import library.encryption.test.CountingSubjectKeys
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import library.schedule.Schedule
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeDataSourceImplEncryptionTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable,
            BusinessDashboardTable,
            BusinessPermissionGrantsTable,
            BusinessWorkingHoursTable,
            BusinessDayOffTable,
            ServiceGroupTable,
            ServiceTable,
            EmployeeTable,
            EmployeeCanProvideServiceTable,
            EmployeeWorkingHoursTable,
            EmployeeDayOffTable,
        )
        val keys = InMemorySubjectKeys()
        val subjectKeys = CountingSubjectKeys(keys)
        val missingKeys = RecordingMissingKeys()
        val sut = EmployeeDataSourceImpl(ContactCipher(subjectKeys, UnkeyedBlindIndex(), missingKeys))
        val businessSut = BusinessDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            businessId = suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { EmployeeTable.selectAll().where { EmployeeTable.id eq id }.single() }
        }

        suspend fun decrypted(userId: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(userId))
            return ciphertext?.let { (decryptor.decrypt(userId, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    private fun Employee.edit(name: String): EmployeeUpdateModel {
        return EmployeeUpdateModel(
            id = id,
            businessId = businessId,
            name = name,
            lastName = lastName,
            phone = phone,
            email = email,
            services = emptyList(),
            schedule = Schedule.empty(),
        )
    }

    @Test
    fun `should store an employee encrypted under the employee's user key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(
            businessId = fixture.businessId,
            name = "Alice",
            lastName = "Smith",
            phone = "+10000000000",
            email = "alice@example.com",
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }

        then()
        val row = fixture.storedRow(created.id)
        assertEquals("Alice", fixture.decrypted(employee.userId, row[EmployeeTable.nameEncrypted]))
        assertEquals("Smith", fixture.decrypted(employee.userId, row[EmployeeTable.lastNameEncrypted]))
        assertEquals("+10000000000", fixture.decrypted(employee.userId, row[EmployeeTable.phoneEncrypted]))
        assertEquals("alice@example.com", fixture.decrypted(employee.userId, row[EmployeeTable.emailEncrypted]))
    }

    @Test
    fun `should read back encrypted employees`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, name = "Alice"))
        }

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertEquals(listOf(created.id), employees.map { it.id })
        assertEquals("Alice", employees.single().name)
    }

    @Test
    fun `should read an employee whose key is missing as empty fields and report it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(businessId = fixture.businessId)
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        fixture.keys.loseKey(employee.userId)

        whenn()
        val read = suspendTransaction { fixture.sut.getEmployeeByUserId(fixture.businessId, employee.userId) }

        then()
        assertEquals(created.id, read?.id)
        assertEquals(listOf("", "", null, null), listOf(read?.name, read?.lastName, read?.phone, read?.email))
        assertEquals(listOf("employee"), fixture.missingKeys.reported)
    }

    @Test
    fun `should encrypt every synced employment of the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Employee.stub(businessId = fixture.businessId, name = "Old", phone = "+10000000001")
        val stored = suspendTransaction { fixture.sut.createEmployee(legacy) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedEmployees(
                legacy.userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        val row = fixture.storedRow(stored.id)
        assertEquals(1, updated)
        assertEquals("New", fixture.decrypted(legacy.userId, row[EmployeeTable.nameEncrypted]))
        assertEquals("new@example.com", fixture.decrypted(legacy.userId, row[EmployeeTable.emailEncrypted]))
        assertEquals("+10000000001", fixture.decrypted(legacy.userId, row[EmployeeTable.phoneEncrypted]))
    }

    @Test
    fun `should keep the stored phone of an employment synced after its key was lost`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(businessId = fixture.businessId, phone = "+10000000001")
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        val before = fixture.storedRow(created.id)[EmployeeTable.phoneEncrypted]
        fixture.keys.loseKey(employee.userId)

        whenn()
        suspendTransaction {
            fixture.sut.updateIntegratedEmployees(
                employee.userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        val after = fixture.storedRow(created.id)
        assertEquals("New", fixture.decrypted(employee.userId, after[EmployeeTable.nameEncrypted]))
        assertArrayEquals(before, after[EmployeeTable.phoneEncrypted])
    }

    @Test
    fun `should fetch the user's key once for every synced employment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId))
            fixture.sut.createEmployee(Employee.stub(businessId = otherBusinessId, userId = userId))
        }
        fixture.subjectKeys.resetCounts()

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedEmployees(
                userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        assertEquals(2, updated)
        assertEquals(listOf(userId), fixture.subjectKeys.encryptorsRequested)
    }

    @Test
    fun `should create no key when the user has no employment to sync`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedEmployees(
                Uuid.random(),
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        assertEquals(0, updated)
        assertEquals(emptyList<Uuid>(), fixture.subjectKeys.encryptorsRequested)
    }

    @Test
    fun `should skip syncing the employments of a user whose key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(businessId = fixture.businessId)
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        fixture.subjectKeys.shred(employee.userId)
        val before = fixture.storedRow(created.id)[EmployeeTable.nameEncrypted]

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedEmployees(
                employee.userId,
                name = "New",
                lastName = "Name",
                email = "new@example.com",
                phone = null,
                updatedAt = Instant.fromEpochSeconds(1_000),
            )
        }

        then()
        assertEquals(0, updated)
        assertTrue(before.contentEquals(fixture.storedRow(created.id)[EmployeeTable.nameEncrypted]))
    }
}
