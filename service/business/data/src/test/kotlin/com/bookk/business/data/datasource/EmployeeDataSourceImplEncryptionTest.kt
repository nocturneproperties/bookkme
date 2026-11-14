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
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import library.schedule.Schedule
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        val subjectKeys = InMemorySubjectKeys()
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

        suspend fun insertLegacy(employee: Employee) {
            suspendTransaction {
                EmployeeTable.insert {
                    it[EmployeeTable.id] = employee.id
                    it[businessId] = this@SutFixture.businessId
                    it[name] = employee.name
                    it[lastName] = employee.lastName
                    it[phone] = employee.phone
                    it[email] = employee.email
                    it[userId] = employee.userId
                }
            }
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
    fun `should store an employee encrypted under the employee's user key and no plaintext`() = runUnitTest {
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
        assertNull(row[EmployeeTable.name])
        assertNull(row[EmployeeTable.lastName])
        assertNull(row[EmployeeTable.phone])
        assertNull(row[EmployeeTable.email])
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
    fun `should read a plaintext employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Employee.stub(businessId = fixture.businessId, name = "Legacy", email = "legacy@example.com")
        fixture.insertLegacy(legacy)

        whenn()
        val read = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, legacy.id) }

        then()
        assertEquals("Legacy", read?.name)
        assertEquals("legacy@example.com", read?.email)
    }

    @Test
    fun `should encrypt a plaintext employee when it is edited`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacy = Employee.stub(businessId = fixture.businessId, name = "Legacy")
        fixture.insertLegacy(legacy)

        whenn()
        val updated = suspendTransaction { fixture.sut.updateEmployee(legacy.edit(name = "Renamed")) }

        then()
        val row = fixture.storedRow(legacy.id)
        assertEquals("Renamed", updated.name)
        assertNull(row[EmployeeTable.name])
        assertNull(row[EmployeeTable.email])
        assertEquals("Renamed", fixture.decrypted(legacy.userId, row[EmployeeTable.nameEncrypted]))
        assertEquals(legacy.email, fixture.decrypted(legacy.userId, row[EmployeeTable.emailEncrypted]))
    }

    @Test
    fun `should read an employee whose key is missing as empty fields and report it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(businessId = fixture.businessId)
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        fixture.subjectKeys.loseKey(employee.userId)

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
        fixture.insertLegacy(legacy)

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
        val row = fixture.storedRow(legacy.id)
        assertEquals(1, updated)
        assertNull(row[EmployeeTable.name])
        assertNull(row[EmployeeTable.phone])
        assertEquals("New", fixture.decrypted(legacy.userId, row[EmployeeTable.nameEncrypted]))
        assertEquals("new@example.com", fixture.decrypted(legacy.userId, row[EmployeeTable.emailEncrypted]))
        assertEquals("+10000000001", fixture.decrypted(legacy.userId, row[EmployeeTable.phoneEncrypted]))
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

    @Test
    fun `should encrypt plaintext employees in batches`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val first = Employee.stub(businessId = fixture.businessId, name = "First")
        val second = Employee.stub(businessId = fixture.businessId, name = "Second")
        fixture.insertLegacy(first)
        fixture.insertLegacy(second)
        suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId)) }

        whenn()
        val firstBatch = suspendTransaction { fixture.sut.encryptPlaintextEmployees(limit = 1) }
        val secondBatch = suspendTransaction { fixture.sut.encryptPlaintextEmployees(limit = 1) }
        val thirdBatch = suspendTransaction { fixture.sut.encryptPlaintextEmployees(limit = 1) }

        then()
        assertEquals(listOf(1, 1, 0), listOf(firstBatch, secondBatch, thirdBatch))
        listOf(first, second).forEach { employee ->
            val row = fixture.storedRow(employee.id)
            assertNull(row[EmployeeTable.name])
            assertEquals(employee.name, fixture.decrypted(employee.userId, row[EmployeeTable.nameEncrypted]))
        }
    }
}
