package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.data.orm.table.EmployeeCanProvideServiceTable
import com.bookk.business.data.orm.table.EmployeeDayOffTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.data.orm.table.EmployeeWorkingHoursTable
import com.bookk.business.data.orm.table.ServiceGroupTable
import com.bookk.business.data.orm.table.ServiceTable
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.core.data.test.createTestDatabase
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImplTest {

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
            ClientTable,
        )
        val sut = UserDataExportDataSourceImpl()
        val businessSut = BusinessDataSourceImpl()
        val employeeSut = EmployeeDataSourceImpl()
        val clientSut = ClientDataSourceImpl()
        val permissionSut = BusinessPermissionDataSourceImpl()
        val userId = Uuid.random()

        suspend fun business(ownerId: Uuid = Uuid.random(), name: String = "Studio") = suspendTransaction {
            businessSut.createBusiness(ownerId, name, "UAH", TimeZone.of("Europe/Kyiv"))
        }

        suspend fun export() = suspendTransaction { sut.export(userId) }
    }

    @Test
    fun `should export nothing for a user without businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.business()

        whenn()
        val export = fixture.export()

        then()
        assertEquals(BusinessUserDataExport.stub(), export)
    }

    @Test
    fun `should export the business the user owns`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owned = fixture.business(ownerId = fixture.userId, name = "Olena's studio")
        val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        suspendTransaction {
            BusinessTable.update({ BusinessTable.id eq owned.id }) {
                it[instagram] = "@studio"
                it[latitude] = 50.45
                it[longitude] = 30.52
                it[this.createdAt] = createdAt
            }
        }

        whenn()
        val export = fixture.export()

        then()
        assertEquals(
            BusinessUserDataExport.OwnedBusinessExport.stub(
                id = owned.id,
                name = "Olena's studio",
                latitude = 50.45,
                longitude = 30.52,
                instagram = "@studio",
                createdAt = createdAt,
            ),
            export.ownedBusiness,
        )
    }

    @Test
    fun `should export the user's employee rows, suspended too, with permissions, hours and days off`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val workplace = fixture.business(name = "Barber")
        val suspendedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val employee = suspendTransaction {
            fixture.employeeSut.createEmployee(
                Employee.stub(
                    businessId = workplace.id,
                    userId = fixture.userId,
                    name = "Olena",
                    lastName = "Koval",
                    phone = "+380501112233",
                    email = "olena@example.com",
                    schedule = Schedule(
                        workingDays = listOf(DayOfWeek.MONDAY),
                        workingHours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(9, 0), LocalTime(13, 0)))),
                        dayOffs = listOf(DayOffRange(LocalDate(2026, 12, 24), LocalDate(2026, 12, 26))),
                    ),
                ),
            )
        }
        suspendTransaction {
            fixture.permissionSut.setPermissions(
                employee,
                mapOf(BusinessResource.CLIENTS to ResourcePermission(view = true, update = true, delete = false)),
            )
            fixture.employeeSut.setSuspendedAt(employee.id, suspendedAt)
        }

        whenn()
        val export = fixture.export()

        then()
        val employment = export.employments.single()
        assertEquals(
            BusinessUserDataExport.EmploymentExport.stub(
                businessId = workplace.id,
                businessName = "Barber",
                name = "Olena",
                lastName = "Koval",
                phone = "+380501112233",
                email = "olena@example.com",
                suspendedAt = suspendedAt,
                permissions = employment.permissions,
                workingHours = listOf(
                    BusinessUserDataExport.WorkingHoursExport("MONDAY", LocalTime(9, 0), LocalTime(13, 0)),
                ),
                daysOff = listOf(BusinessUserDataExport.DayOffExport(LocalDate(2026, 12, 24), LocalDate(2026, 12, 26))),
            ),
            employment,
        )
        assertEquals(
            BusinessUserDataExport.PermissionExport("CLIENTS", view = true, update = true, delete = false),
            employment.permissions.single { it.resource == "CLIENTS" },
        )
    }

    @Test
    fun `should export the user's client rows as each business keeps them`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val salon = fixture.business(name = "Salon")
        suspendTransaction {
            fixture.clientSut.getOrCreateIntegratedClient(
                salon.id,
                Client.Integrated.stub(
                    userId = fixture.userId,
                    name = "Olena",
                    lastName = "Koval",
                    phone = "+380501112233",
                    email = "olena@example.com",
                ),
            )
        }
        val clientId = suspendTransaction { fixture.clientSut.getClientByUserId(salon.id, fixture.userId) }!!.id
        val lastActivityAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        suspendTransaction {
            ClientTable.update({ ClientTable.id eq clientId }) {
                it[description] = "allergic to latex"
                it[this.lastActivityAt] = lastActivityAt
            }
        }
        suspendTransaction {
            fixture.clientSut.createDetachedClient(
                salon.id,
                Client.Detached.stub(phone = "+380509998877", email = "other@example.com"),
            )
        }

        whenn()
        val export = fixture.export()

        then()
        assertEquals(
            listOf(
                BusinessUserDataExport.ClientRecordExport.stub(
                    businessId = salon.id,
                    businessName = "Salon",
                    name = "Olena",
                    lastName = "Koval",
                    phone = "+380501112233",
                    email = "olena@example.com",
                    description = "allergic to latex",
                    lastActivityAt = lastActivityAt,
                ),
            ),
            export.clientRecords,
        )
    }

    @Test
    fun `should export which business the user's dashboard shows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owned = fixture.business(ownerId = fixture.userId)
        suspendTransaction { fixture.businessSut.setDashboardBusiness(fixture.userId, owned.id) }

        whenn()
        val export = fixture.export()

        then()
        assertEquals(owned.id, export.dashboardBusinessId)
    }

    @Test
    fun `should not export another user's rows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val other = Uuid.random()
        val business = fixture.business(ownerId = other)
        suspendTransaction {
            fixture.employeeSut.createEmployee(Employee.stub(businessId = business.id, userId = other))
        }
        suspendTransaction {
            fixture.clientSut.getOrCreateIntegratedClient(business.id, Client.Integrated.stub(userId = other))
        }

        whenn()
        val export = fixture.export()

        then()
        assertNull(export.ownedBusiness)
        assertEquals(emptyList<BusinessUserDataExport.EmploymentExport>(), export.employments)
        assertEquals(emptyList<BusinessUserDataExport.ClientRecordExport>(), export.clientRecords)
    }
}
