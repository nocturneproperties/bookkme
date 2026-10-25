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
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable, BusinessDashboardTable, BusinessPermissionGrantsTable, BusinessWorkingHoursTable, BusinessDayOffTable,
            ServiceGroupTable, ServiceTable, EmployeeTable, EmployeeCanProvideServiceTable,
            EmployeeWorkingHoursTable, EmployeeDayOffTable
        )
        val sut = EmployeeDataSourceImpl()
        val businessSut = BusinessDataSourceImpl()
        val serviceSut = ServiceDataSourceImpl()
        val permissionSut = BusinessPermissionDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            businessId = suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }

        suspend fun createService(name: String = "stub-service"): Service {
            val group = suspendTransaction {
                serviceSut.createServiceGroup(ServiceGroup.stub(businessId = businessId, name = "group-$name"))
            }
            return suspendTransaction {
                serviceSut.createService(Service.stub(businessId = businessId, group = group, name = name))
            }
        }
    }

    @Test
    fun `should create employee and retrieve by business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(
            businessId = fixture.businessId,
            name = "Alice",
            lastName = "Smith",
            phone = "+1234567890",
            email = "alice@test.com"
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        val employees = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertEquals(1, employees.size)
        assertEquals("Alice", employees.first().name)
        assertEquals("Smith", employees.first().lastName)
        assertEquals("+1234567890", employees.first().phone)
        assertEquals("alice@test.com", employees.first().email)
        assertEquals(fixture.businessId, created.businessId)
    }

    @Test
    fun `should create employee without phone and email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Employee.stub(
            businessId = fixture.businessId,
            name = "Bob",
            lastName = "Jones",
            phone = null,
            email = null
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertNotNull(found)
        assertNull(found!!.phone)
        assertNull(found.email)
    }

    @Test
    fun `should create employee linked to user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val employee = Employee.stub(businessId = fixture.businessId, userId = userId)

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }

        then()
        assertEquals(userId, created.userId)
    }

    @Test
    fun `should return empty list when no employees exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertTrue(employees.isEmpty())
    }

    @Test
    fun `should return null when employee belongs to another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getEmployee(Uuid.random(), created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should delete employee and return true`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }

        whenn()
        val deleted = suspendTransaction { fixture.sut.deleteEmployee(fixture.businessId, created.id) }
        val remaining = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertTrue(deleted)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `should return false when deleting non-existent employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val deleted = suspendTransaction { fixture.sut.deleteEmployee(fixture.businessId, Uuid.random()) }

        then()
        assertFalse(deleted)
    }

    @Test
    fun `should update employees linked to user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val created = suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(
                    businessId = fixture.businessId,
                    name = "Old",
                    lastName = "Name",
                    phone = "+1111111111",
                    email = "old@test.com",
                    userId = userId
                )
            )
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedEmployees(userId, "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000))
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(1, updated)
        assertNotNull(found)
        assertEquals("New", found!!.name)
        assertEquals("Surname", found.lastName)
        assertEquals("+1111111111", found.phone)
        assertEquals("new@test.com", found.email)
        assertEquals(userId, found.userId)
    }

    @Test
    fun `should not update employees belonging to another user when syncing profile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(
                    businessId = fixture.businessId,
                    name = "Keep",
                    lastName = "Me",
                    userId = Uuid.random()
                )
            )
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateIntegratedEmployees(Uuid.random(), "New", "Surname", "new@test.com", null, Instant.fromEpochMilliseconds(1000))
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(0, updated)
        assertEquals("Keep", found!!.name)
    }

    @Test
    fun `should return no services when employee has no assignments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertTrue(found!!.services.isEmpty())
    }

    @Test
    fun `should expose assigned services on employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val service = fixture.createService(name = "haircut")
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(service))) }

        whenn()
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, employee.id) }

        then()
        assertEquals(1, found!!.services.size)
        assertEquals(service.id, found.services.first().id)
        assertEquals("haircut", found.services.first().name)
    }

    @Test
    fun `should expose assigned services when listing employees of business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val first = fixture.createService(name = "haircut")
        val second = fixture.createService(name = "shave")
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(first, second))) }

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertEquals(1, employees.size)
        assertEquals(
            setOf(first.id, second.id),
            employees.first().services.map { it.id }.toSet()
        )
    }

    @Test
    fun `should persist services provided on employee creation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val service = fixture.createService(name = "massage")

        whenn()
        val created = suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(businessId = fixture.businessId, services = listOf(service))
            )
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(listOf(service.id), created.services.map { it.id })
        assertEquals(listOf(service.id), found!!.services.map { it.id })
    }

    @Test
    fun `should list service ids assigned through update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val service = fixture.createService()

        whenn()
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(service))) }
        val serviceIds = suspendTransaction { fixture.sut.getServiceIds(employee.id) }

        then()
        assertEquals(listOf(service.id), serviceIds)
    }

    @Test
    fun `should not duplicate service row when employee is updated with the same services twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val service = fixture.createService()

        whenn()
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(service))) }
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(service))) }
        val serviceIds = suspendTransaction { fixture.sut.getServiceIds(employee.id) }

        then()
        assertEquals(1, serviceIds.size)
    }

    @Test
    fun `should return employees that can provide a service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val provider = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, name = "Provider"))
        }
        suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, name = "Other"))
        }
        val service = fixture.createService()
        suspendTransaction { fixture.sut.updateEmployee(updateModel(provider).copy(services = listOf(service))) }

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployeesByService(service.id) }

        then()
        assertEquals(1, employees.size)
        assertEquals("Provider", employees.first().name)
    }

    @Test
    fun `should cascade delete assignments when employee is deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val service = fixture.createService()
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(service))) }

        whenn()
        suspendTransaction { fixture.sut.deleteEmployee(fixture.businessId, employee.id) }
        val employees = suspendTransaction { fixture.sut.getEmployeesByService(service.id) }

        then()
        assertTrue(employees.isEmpty())
    }

    @Test
    fun `should cascade delete assignments when service is deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val service = fixture.createService()
        suspendTransaction { fixture.sut.updateEmployee(updateModel(employee).copy(services = listOf(service))) }

        whenn()
        suspendTransaction { fixture.serviceSut.deleteService(service.id, fixture.businessId) }
        val serviceIds = suspendTransaction { fixture.sut.getServiceIds(employee.id) }

        then()
        assertTrue(serviceIds.isEmpty())
    }

    @Test
    fun `should return employee by user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getEmployeeByUserId(fixture.businessId, userId) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found?.id)
        assertEquals(userId, found?.userId)
    }

    @Test
    fun `should reject a second employee row for the same user in the same business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId)) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId)) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should allow the same user to be an employee of different businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId)) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = otherBusinessId, userId = userId)) }
        }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return null when user is not an employee of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId))
        }

        whenn()
        val otherUser = suspendTransaction { fixture.sut.getEmployeeByUserId(fixture.businessId, Uuid.random()) }
        val otherBusiness = suspendTransaction { fixture.sut.getEmployeeByUserId(Uuid.random(), userId) }

        then()
        assertNull(otherUser)
        assertNull(otherBusiness)
    }

    @Test
    fun `should have no working days and no day offs by default`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertTrue(created.schedule.activeDays().isEmpty())
        assertTrue(created.schedule.dayOffs.isEmpty())
        assertTrue(found!!.schedule.activeDays().isEmpty())
        assertTrue(found.schedule.dayOffs.isEmpty())
    }

    @Test
    fun `should persist explicitly assigned working days and hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            workingHours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(9, 0), LocalTime(13, 0))))
        )
        val employee = Employee.stub(businessId = fixture.businessId, schedule = schedule)

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), created.schedule.activeDays())
        assertEquals(listOf(WorkHour(LocalTime(9, 0), LocalTime(13, 0))), created.schedule.workingHours()[DayOfWeek.MONDAY])
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), found!!.schedule.activeDays())
        assertEquals(listOf(WorkHour(LocalTime(9, 0), LocalTime(13, 0))), found.schedule.workingHours()[DayOfWeek.MONDAY])
    }

    @Test
    fun `should persist explicitly assigned day offs`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val dayOff = DayOffRange(LocalDate(2026, 1, 1), LocalDate(2026, 1, 2))
        val schedule = Schedule(workingDays = emptyList(), workingHours = emptyMap(), dayOffs = listOf(dayOff))
        val employee = Employee.stub(businessId = fixture.businessId, schedule = schedule)

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(employee) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(listOf(dayOff), created.schedule.dayOffs)
        assertEquals(listOf(dayOff), found!!.schedule.dayOffs)
    }

    @Test
    fun `should update employee name lastname phone and email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(businessId = fixture.businessId, name = "Old", lastName = "Name", phone = "+1000", email = "old@test.com")
            )
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateEmployee(
                updateModel(created).copy(name = "New", lastName = "Surname", phone = "+2000", email = "new@test.com")
            )
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals("New", updated.name)
        assertEquals("Surname", updated.lastName)
        assertEquals("+2000", updated.phone)
        assertEquals("new@test.com", updated.email)
        assertEquals("New", found!!.name)
        assertEquals("Surname", found.lastName)
        assertEquals("+2000", found.phone)
        assertEquals("new@test.com", found.email)
    }

    @Test
    fun `should replace employee schedule on update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId))
        }
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.WEDNESDAY),
            workingHours = mapOf(DayOfWeek.WEDNESDAY to listOf(WorkHour(LocalTime(8, 0), LocalTime(12, 0)))),
            dayOffs = listOf(DayOffRange(LocalDate(2026, 2, 1), LocalDate(2026, 2, 2)))
        )

        whenn()
        val updated = suspendTransaction { fixture.sut.updateEmployee(updateModel(created).copy(schedule = schedule)) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(listOf(DayOfWeek.WEDNESDAY), updated.schedule.activeDays())
        assertEquals(schedule.dayOffs, updated.schedule.dayOffs)
        assertEquals(listOf(DayOfWeek.WEDNESDAY), found!!.schedule.activeDays())
        assertEquals(schedule.dayOffs, found.schedule.dayOffs)
    }

    @Test
    fun `should replace employee services on update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val kept = fixture.createService(name = "kept")
        val dropped = fixture.createService(name = "dropped")
        val added = fixture.createService(name = "added")
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, services = listOf(kept, dropped)))
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateEmployee(updateModel(created).copy(services = listOf(kept, added)))
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(setOf(kept.id, added.id), updated.services.map { it.id }.toSet())
        assertEquals(setOf(kept.id, added.id), found!!.services.map { it.id }.toSet())
    }

    @Test
    fun `should throw not found when updating employee that does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.updateEmployee(updateModel(Employee.stub(id = Uuid.random(), businessId = fixture.businessId))) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should anonymize employee PII and clear schedule and services for the deleted user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val service = fixture.createService()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.WEDNESDAY),
            workingHours = mapOf(DayOfWeek.WEDNESDAY to listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0)))),
            dayOffs = listOf(DayOffRange(LocalDate(2099, 1, 1), LocalDate(2099, 1, 2)))
        )
        val created = suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(
                    businessId = fixture.businessId,
                    userId = userId,
                    name = "Alice",
                    lastName = "Smith",
                    phone = "+1234567890",
                    email = "alice@test.com",
                    services = listOf(service),
                    schedule = schedule
                )
            )
        }

        whenn()
        val affected = suspendTransaction { fixture.sut.anonymizeEmployeesByUserId(userId) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(1, affected)
        assertNotNull(found)
        assertEquals("Deleted User", found!!.name)
        assertEquals("", found.lastName)
        assertNull(found.phone)
        assertNull(found.email)
        assertTrue(found.services.isEmpty())
        assertTrue(found.schedule.activeDays().isEmpty())
        assertTrue(found.schedule.dayOffs.isEmpty())
    }

    @Test
    fun `should return zero when anonymizing employees for a user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val affected = suspendTransaction { fixture.sut.anonymizeEmployeesByUserId(Uuid.random()) }

        then()
        assertEquals(0, affected)
    }

    @Test
    fun `should persist employee permissions on creation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val permissions = BusinessPermissions.stub(clients = ResourcePermission(view = true, update = true, delete = false))

        whenn()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, permissions = permissions))
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(permissions, created.permissions)
        assertEquals(permissions, found!!.permissions)
    }

    @Test
    fun `should return each employee with own permissions when listing employees of business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val owner = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, permissions = BusinessPermissions.FULL))
        }
        val viewer = suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(businessId = fixture.businessId, permissions = BusinessPermissions.stub(services = ResourcePermission(view = true, update = false, delete = false)))
            )
        }

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertEquals(2, employees.size)
        assertEquals(BusinessPermissions.FULL, employees.single { it.id == owner.id }.permissions)
        assertEquals(viewer.permissions, employees.single { it.id == viewer.id }.permissions)
    }

    @Test
    fun `should return employee with no permissions when user has no grants`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, permissions = BusinessPermissions.FULL))
        }
        suspendTransaction { fixture.permissionSut.deleteUserPermissions(created.userId) }

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertEquals(1, employees.size)
        assertEquals(BusinessPermissions.NONE, employees.single().permissions)
    }

    @Test
    fun `should not mix in grants the same user holds in another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(businessId = otherBusinessId, userId = userId, permissions = BusinessPermissions.FULL)
            )
        }
        val permissions = BusinessPermissions.stub(appointments = ResourcePermission(view = true, update = false, delete = false))
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, userId = userId, permissions = permissions))
        }

        whenn()
        val byId = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }
        val byUserId = suspendTransaction { fixture.sut.getEmployeeByUserId(fixture.businessId, userId) }
        val listed = suspendTransaction { fixture.sut.getEmployees(fixture.businessId) }

        then()
        assertEquals(permissions, byId!!.permissions)
        assertEquals(permissions, byUserId!!.permissions)
        assertEquals(listOf(permissions), listed.map { it.permissions })
    }

    @Test
    fun `should reflect a permission change on the next employee read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, permissions = BusinessPermissions.NONE))
        }
        val granted = ResourcePermission(view = true, update = true, delete = false)

        whenn()
        suspendTransaction {
            fixture.permissionSut.setPermissions(created, mapOf(BusinessResource.CLIENTS to granted))
        }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(BusinessPermissions.stub(clients = granted), found!!.permissions)
    }

    @Test
    fun `should keep permissions when employee is updated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val permissions = BusinessPermissions.stub(employees = ResourcePermission(view = true, update = false, delete = false))
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, permissions = permissions))
        }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateEmployee(updateModel(created).copy(name = "Renamed")) }

        then()
        assertEquals("Renamed", updated.name)
        assertEquals(permissions, updated.permissions)
    }

    @Test
    fun `should include permissions when listing employees that can provide a service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val service = fixture.createService()
        val permissions = BusinessPermissions.stub(services = ResourcePermission(view = true, update = false, delete = false))
        suspendTransaction {
            fixture.sut.createEmployee(
                Employee.stub(businessId = fixture.businessId, services = listOf(service), permissions = permissions)
            )
        }

        whenn()
        val employees = suspendTransaction { fixture.sut.getEmployeesByService(service.id) }

        then()
        assertEquals(listOf(permissions), employees.map { it.permissions })
    }

    @Test
    fun `should be active by default`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val created = suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId)) }

        then()
        assertNull(created.suspendedAt)
    }

    @Test
    fun `should persist the suspension time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId)) }
        val suspendedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

        whenn()
        val updated = suspendTransaction { fixture.sut.setSuspendedAt(created.id, suspendedAt) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertEquals(suspendedAt, updated.suspendedAt)
        assertEquals(suspendedAt, found?.suspendedAt)
    }

    @Test
    fun `should clear the suspension time on reinstatement`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId)) }
        suspendTransaction { fixture.sut.setSuspendedAt(created.id, Instant.fromEpochMilliseconds(1_700_000_000_000)) }

        whenn()
        suspendTransaction { fixture.sut.setSuspendedAt(created.id, null) }
        val found = suspendTransaction { fixture.sut.getEmployee(fixture.businessId, created.id) }

        then()
        assertNull(found?.suspendedAt)
    }

    @Test
    fun `should keep stored permissions while suspended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createEmployee(Employee.stub(businessId = fixture.businessId, permissions = BusinessPermissions.VIEW_ONLY))
        }

        whenn()
        val updated = suspendTransaction { fixture.sut.setSuspendedAt(created.id, Instant.fromEpochMilliseconds(1_700_000_000_000)) }

        then()
        assertEquals(BusinessPermissions.VIEW_ONLY, updated.permissions)
    }

    @Test
    fun `should throw not found when suspending employee that does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.setSuspendedAt(Uuid.random(), Instant.fromEpochMilliseconds(1)) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    private fun updateModel(employee: Employee) = EmployeeUpdateModel(
        id = employee.id,
        businessId = employee.businessId,
        name = employee.name,
        lastName = employee.lastName,
        phone = employee.phone,
        email = employee.email,
        services = employee.services,
        schedule = employee.schedule
    )
}
