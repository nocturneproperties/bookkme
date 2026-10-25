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
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable,
            BusinessDashboardTable,
            BusinessPermissionGrantsTable,
            BusinessWorkingHoursTable,
            BusinessDayOffTable,
            EmployeeTable,
            EmployeeCanProvideServiceTable,
            EmployeeWorkingHoursTable,
            EmployeeDayOffTable
        )
        val sut = BusinessDataSourceImpl()
        val employeeSut = EmployeeDataSourceImpl()
    }

    private fun updateModel(
        id: Uuid,
        name: String? = null,
        schedule: Schedule? = null,
        dayOffs: List<DayOffRange> = emptyList()
    ) = BusinessUpdateModel(
        id = id,
        name = name,
        description = null,
        address = null,
        location = null,
        currencyCode = null,
        timeZone = null,
        socials = null,
        schedule = schedule?.copy(dayOffs = dayOffs)
    )

    @Test
    fun `should create business and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val created = suspendTransaction {
            fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC)
        }
        val found = suspendTransaction { fixture.sut.getBusinessById(created.id) }

        then()
        assertNotNull(found)
        assertEquals("Salon", found!!.name)
        assertEquals("USD", found.currencyCode)
    }

    @Test
    fun `should return null when business not found`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getBusinessById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should return the stored currency when locking an existing business exclusively`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction {
            fixture.sut.createBusiness(Uuid.random(), "Salon", "EUR", TimeZone.UTC)
        }

        whenn()
        val locked = suspendTransaction { fixture.sut.lockBusiness(created.id, RowLock.EXCLUSIVE) }

        then()
        assertEquals(LockedBusiness("EUR"), locked)
    }

    @Test
    fun `should return the stored currency when locking an existing business shared`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction {
            fixture.sut.createBusiness(Uuid.random(), "Salon", "EUR", TimeZone.UTC)
        }

        whenn()
        val locked = suspendTransaction { fixture.sut.lockBusiness(created.id, RowLock.SHARED) }

        then()
        assertEquals(LockedBusiness("EUR"), locked)
    }

    @Test
    fun `should not lock an unknown business`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val locked = suspendTransaction { fixture.sut.lockBusiness(Uuid.random(), RowLock.EXCLUSIVE) }

        then()
        assertNull(locked)
    }

    @Test
    fun `should report business exists after creation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC) }

        whenn()
        val exists = suspendTransaction { fixture.sut.isBusinessExist(userId) }

        then()
        assertTrue(exists)
    }

    @Test
    fun `should report business does not exist for unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val exists = suspendTransaction { fixture.sut.isBusinessExist(Uuid.random()) }

        then()
        assertFalse(exists)
    }

    @Test
    fun `should report user as owner of the business they created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC) }

        whenn()
        val isOwner = suspendTransaction { fixture.sut.isOwner(userId, business.id) }

        then()
        assertTrue(isOwner)
    }

    @Test
    fun `should not report user as owner of another users business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        val otherUserId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(ownerId, "Salon", "USD", TimeZone.UTC) }
        suspendTransaction { fixture.sut.createBusiness(otherUserId, "Barber", "USD", TimeZone.UTC) }

        whenn()
        val isOwner = suspendTransaction { fixture.sut.isOwner(otherUserId, business.id) }

        then()
        assertFalse(isOwner)
    }

    @Test
    fun `should expose the creating user as the business owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC) }

        whenn()
        val business = suspendTransaction { fixture.sut.getBusinessById(created.id) }

        then()
        assertEquals(userId, created.ownerId)
        assertEquals(userId, business?.ownerId)
    }

    @Test
    fun `should create business with default working schedule`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val created = suspendTransaction { fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC) }

        then()
        assertEquals(
            DayOfWeek.entries.filter { it < DayOfWeek.SATURDAY },
            created.schedule.activeDays().sorted()
        )
        assertEquals(
            listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))),
            created.schedule[DayOfWeek.MONDAY].workingTime
        )
        assertTrue(created.schedule.dayOffs.isEmpty())
    }

    @Test
    fun `should update business schedule and day offs`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(
                DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))
            )
        )
        val dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))

        whenn()
        suspendTransaction {
            fixture.sut.updateBusiness(updateModel(created.id, schedule = schedule, dayOffs = dayOffs), Clock.System.now())
        }
        val found = suspendTransaction { fixture.sut.getBusinessById(created.id) }

        then()
        assertEquals(listOf(DayOfWeek.SATURDAY), found!!.schedule.activeDays())
        assertEquals(LocalTime(10, 0), found.schedule[DayOfWeek.SATURDAY].workingTime.single().from)
        assertEquals(LocalTime(14, 0), found.schedule[DayOfWeek.SATURDAY].workingTime.single().to)
        assertEquals(dayOffs, found.schedule.dayOffs)
    }

    @Test
    fun `should persist working hours under the day they are keyed under`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY),
            workingHours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0))))
        )

        whenn()
        suspendTransaction { fixture.sut.updateBusiness(updateModel(created.id, schedule = schedule), Clock.System.now()) }
        val found = suspendTransaction { fixture.sut.getBusinessById(created.id) }

        then()
        assertEquals(listOf(DayOfWeek.MONDAY), found!!.schedule.activeDays())
        assertEquals(LocalTime(10, 0), found.schedule[DayOfWeek.MONDAY].workingTime.single().from)
        assertTrue(found.schedule[DayOfWeek.FRIDAY].workingTime.isEmpty())
    }

    @Test
    fun `should persist the supplied updated at on the business row`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        val updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

        whenn()
        suspendTransaction { fixture.sut.updateBusiness(updateModel(created.id, name = "New name"), updatedAt) }
        val stored = suspendTransaction {
            BusinessTable.select(BusinessTable.updatedAt)
                .where { BusinessTable.id eq created.id }
                .single()[BusinessTable.updatedAt]
        }

        then()
        assertEquals(updatedAt, stored)
    }

    @Test
    fun `should keep schedule when update does not contain it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }

        whenn()
        suspendTransaction { fixture.sut.updateBusiness(updateModel(created.id, name = "New name"), Clock.System.now()) }
        val found = suspendTransaction { fixture.sut.getBusinessById(created.id) }

        then()
        assertEquals("New name", found!!.name)
        assertEquals(created.schedule.activeDays().sorted(), found.schedule.activeDays().sorted())
        assertEquals(
            created.schedule[DayOfWeek.MONDAY].workingTime,
            found.schedule[DayOfWeek.MONDAY].workingTime
        )
    }

    @Test
    fun `should delete day offs in the past`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        val future = DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31))
        suspendTransaction {
            fixture.sut.updateBusiness(
                updateModel(
                    created.id,
                    schedule = created.schedule,
                    dayOffs = listOf(DayOffRange(LocalDate(2020, 1, 1), LocalDate(2020, 1, 2)), future)
                ),
                Clock.System.now()
            )
        }

        whenn()
        suspendTransaction { fixture.sut.deleteDayOffsInThePast() }
        val found = suspendTransaction { fixture.sut.getBusinessById(created.id) }

        then()
        assertEquals(listOf(future), found!!.schedule.dayOffs)
    }

    @Test
    fun `should retrieve dashboard business for user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.createBusiness(userId, "Dashboard Salon", "USD", TimeZone.UTC) }

        whenn()
        val dashboard = suspendTransaction { fixture.sut.getDashboardBusiness(userId) }

        then()
        assertNotNull(dashboard)
        assertEquals(created.id, dashboard!!.id)
    }

    @Test
    fun `should return null dashboard for unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val dashboard = suspendTransaction { fixture.sut.getDashboardBusiness(Uuid.random()) }

        then()
        assertNull(dashboard)
    }

    @Test
    fun `should set the dashboard business for a user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC) }
        val otherBusiness = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Other Salon", "USD", TimeZone.UTC) }

        whenn()
        suspendTransaction { fixture.sut.setDashboardBusiness(userId, otherBusiness.id) }
        val dashboard = suspendTransaction { fixture.sut.getDashboardBusiness(userId) }

        then()
        assertNotNull(dashboard)
        assertEquals(otherBusiness.id, dashboard!!.id)
        assertNotEquals(created.id, dashboard.id)
    }

    @Test
    fun `should get user businesses with dashboard id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.createBusiness(userId, "Salon", "USD", TimeZone.UTC) }

        whenn()
        val result = suspendTransaction { fixture.sut.getUserBusinesses(userId) }

        then()
        assertEquals(1, result.businesses.size)
        assertEquals(created.id, result.dashboardId)
    }

    @Test
    fun `should include businesses where user is an employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val ownBusiness = suspendTransaction { fixture.sut.createBusiness(userId, "Own Salon", "USD", TimeZone.UTC) }
        val otherBusiness = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Other Salon", "USD", TimeZone.UTC) }
        suspendTransaction { fixture.employeeSut.createEmployee(Employee.stub(businessId = otherBusiness.id, userId = userId)) }

        whenn()
        val result = suspendTransaction { fixture.sut.getUserBusinesses(userId) }

        then()
        assertEquals(setOf(ownBusiness.id, otherBusiness.id), result.businesses.map { it.id }.toSet())
    }

    @Test
    fun `should not duplicate a business the user both owns and is employed at`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(userId, "Own Salon", "USD", TimeZone.UTC) }
        suspendTransaction { fixture.employeeSut.createEmployee(Employee.stub(businessId = business.id, userId = userId)) }

        whenn()
        val result = suspendTransaction { fixture.sut.getUserBusinesses(userId) }

        then()
        assertEquals(listOf(business.id), result.businesses.map { it.id })
    }

    @Test
    fun `should exclude businesses where the user is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val ownBusiness = suspendTransaction { fixture.sut.createBusiness(userId, "Own Salon", "USD", TimeZone.UTC) }
        val activeBusiness = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Active Salon", "USD", TimeZone.UTC) }
        val suspendedBusiness = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Suspended Salon", "USD", TimeZone.UTC) }
        suspendTransaction {
            fixture.employeeSut.createEmployee(Employee.stub(businessId = activeBusiness.id, userId = userId))
            val suspended = fixture.employeeSut.createEmployee(Employee.stub(businessId = suspendedBusiness.id, userId = userId))
            fixture.employeeSut.setSuspendedAt(suspended.id, Instant.fromEpochMilliseconds(1))
        }

        whenn()
        val result = suspendTransaction { fixture.sut.getUserBusinesses(userId) }

        then()
        assertEquals(setOf(ownBusiness.id, activeBusiness.id), result.businesses.map { it.id }.toSet())
    }

    @Test
    fun `should include a business again once the employee is reinstated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        suspendTransaction {
            val employee = fixture.employeeSut.createEmployee(Employee.stub(businessId = business.id, userId = userId))
            fixture.employeeSut.setSuspendedAt(employee.id, Instant.fromEpochMilliseconds(1))
            fixture.employeeSut.setSuspendedAt(employee.id, null)
        }

        whenn()
        val result = suspendTransaction { fixture.sut.getUserBusinesses(userId) }

        then()
        assertEquals(listOf(business.id), result.businesses.map { it.id })
    }

    @Test
    fun `should not return a dashboard id pointing at a business where the user is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        suspendTransaction {
            val employee = fixture.employeeSut.createEmployee(Employee.stub(businessId = business.id, userId = userId))
            fixture.sut.setDashboardBusiness(userId, business.id)
            fixture.employeeSut.setSuspendedAt(employee.id, Instant.fromEpochMilliseconds(1))
        }

        whenn()
        val result = suspendTransaction { fixture.sut.getUserBusinesses(userId) }

        then()
        assertTrue(result.businesses.isEmpty())
        assertNull(result.dashboardId)
    }

    @Test
    fun `should return no dashboard business when the user is suspended there`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        suspendTransaction {
            val employee = fixture.employeeSut.createEmployee(Employee.stub(businessId = business.id, userId = userId))
            fixture.sut.setDashboardBusiness(userId, business.id)
            fixture.employeeSut.setSuspendedAt(employee.id, Instant.fromEpochMilliseconds(1))
        }

        whenn()
        val dashboard = suspendTransaction { fixture.sut.getDashboardBusiness(userId) }

        then()
        assertNull(dashboard)
    }

    @Test
    fun `should return the dashboard business again once the employee is reinstated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = suspendTransaction { fixture.sut.createBusiness(Uuid.random(), "Salon", "USD", TimeZone.UTC) }
        suspendTransaction {
            val employee = fixture.employeeSut.createEmployee(Employee.stub(businessId = business.id, userId = userId))
            fixture.sut.setDashboardBusiness(userId, business.id)
            fixture.employeeSut.setSuspendedAt(employee.id, Instant.fromEpochMilliseconds(1))
            fixture.employeeSut.setSuspendedAt(employee.id, null)
        }

        whenn()
        val dashboard = suspendTransaction { fixture.sut.getDashboardBusiness(userId) }

        then()
        assertEquals(business.id, dashboard?.id)
    }

    // deleteUserBusinesses uses deleteReturning which is not supported by Exposed's H2 dialect
}
