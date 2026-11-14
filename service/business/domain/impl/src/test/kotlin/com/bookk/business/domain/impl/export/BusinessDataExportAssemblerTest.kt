package com.bookk.business.domain.impl.export

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.export.BusinessClientExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.DayOffExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.PermissionExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.WorkingHoursExport
import com.bookk.business.domain.api.export.PendingInvitationExport
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.datasource.BusinessDataExportDataSource
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessDataExportAssemblerTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val serviceDataSource = mockk<ServiceDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val exportDataSource = mockk<BusinessDataExportDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val exportBusinessAppointments = mockk<ExportBusinessAppointments>()
        val eventProducer = mockk<StandardEventProducer>()
        val clock = TestClock()
        val sut = BusinessDataExportAssembler(
            businessDataSource,
            serviceDataSource,
            employeeDataSource,
            exportDataSource,
            transactionManager,
            exportBusinessAppointments,
            eventProducer,
            clock,
        )
        val ownerId = Uuid.random()
        val monday = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY),
            workingHours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(9, 0), LocalTime(18, 0)))),
            dayOffs = listOf(DayOffRange(LocalDate(2026, 12, 31), LocalDate(2027, 1, 1))),
        )
        val business = Business.stub(
            name = "Studio",
            timeZone = TimeZone.of("Europe/Kyiv"),
            location = Business.Location(50.45, 30.52),
            currencyCode = "UAH",
            socials = listOf(
                Business.Social(Business.SocialKind.INSTAGRAM, "@studio"),
                Business.Social(Business.SocialKind.PHONE, ""),
            ),
            schedule = monday,
            ownerId = ownerId,
            clientDataRetentionMonths = 24,
        )
        val group = ServiceGroup.stub(businessId = business.id, name = "Hair")
        val haircut = Service.stub(
            businessId = business.id,
            group = group,
            name = "Haircut",
            duration = 45.minutes,
            price = Money.parse("UAH 450.00"),
        )
        val appointments = AppointmentsBusinessDataExport.stub(
            settings = AppointmentsBusinessDataExport.SettingsExport(true, true, 10, ""),
        )
        val readInTransaction = mutableListOf<Boolean>()
        val sentEvents = mutableListOf<BusinessEvent.ExportReady>()

        init {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.getBusinessById(business.id) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                business
            }
            coEvery { serviceDataSource.getServiceGroups(business.id) } returns listOf(group)
            coEvery { serviceDataSource.getServices(business.id) } returns listOf(haircut)
            coEvery { employeeDataSource.getEmployees(business.id) } returns emptyList()
            coEvery { exportDataSource.getPendingInvitations(business.id) } returns emptyList()
            coEvery { exportDataSource.getClients(business.id) } returns emptyList()
            coEvery { exportBusinessAppointments(business.id) } returns Result.success(appointments)
            coEvery { eventProducer.send(any<BusinessEvent.ExportReady>(), any()) } coAnswers
                { sentEvents += firstArg<BusinessEvent.ExportReady>() }
        }

        suspend fun export(): BusinessDataExport = Json.decodeFromString(sut.assemble(business.id))
    }

    @Test
    fun `should export the business profile with its contacts, schedule and retention period`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val export = fixture.export()

        then()
        assertEquals(
            BusinessDataExport.ProfileExport(
                name = "Studio",
                description = fixture.business.description,
                address = fixture.business.address,
                latitude = 50.45,
                longitude = 30.52,
                currency = "UAH",
                timeZone = "Europe/Kyiv",
                contacts = listOf(BusinessDataExport.ContactExport("INSTAGRAM", "@studio")),
                workingHours = listOf(WorkingHoursExport("MONDAY", LocalTime(9, 0), LocalTime(18, 0))),
                daysOff = listOf(DayOffExport(LocalDate(2026, 12, 31), LocalDate(2027, 1, 1))),
                clientDataRetentionMonths = 24,
            ),
            export.business,
        )
        assertEquals(fixture.business.id, export.businessId)
        assertEquals(fixture.clock.now, export.generatedAt)
    }

    @Test
    fun `should export the services under their groups`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val export = fixture.export()

        then()
        assertEquals(
            listOf(
                BusinessDataExport.ServiceGroupExport(
                    fixture.group.id,
                    "Hair",
                    listOf(
                        BusinessDataExport.ServiceExport(
                            fixture.haircut.id,
                            "Haircut",
                            "UAH 450.00",
                            durationMinutes = 45,
                            available = true,
                        ),
                    ),
                ),
            ),
            export.serviceGroups,
        )
    }

    @Test
    fun `should export every employee with role, services, schedule and permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = Employee.stub(
            businessId = fixture.business.id,
            userId = fixture.ownerId,
            name = "Olena",
            permissions = BusinessPermissions.FULL,
        )
        val suspendedAt = Instant.fromEpochMilliseconds(1_000)
        val stylist = Employee.stub(
            businessId = fixture.business.id,
            name = "Ivan",
            lastName = "Petrenko",
            phone = "+380509998877",
            email = "ivan@example.com",
            services = listOf(fixture.haircut),
            schedule = fixture.monday,
            createdAt = Instant.fromEpochMilliseconds(500),
            suspendedAt = suspendedAt,
        )
        coEvery { fixture.employeeDataSource.getEmployees(fixture.business.id) } returns listOf(owner, stylist)

        whenn()
        val export = fixture.export()

        then()
        assertEquals(
            listOf(BusinessDataExport.Role.OWNER, BusinessDataExport.Role.EMPLOYEE),
            export.employees.map {
                it.role
            },
        )
        val exported = export.employees[1]
        assertEquals(
            BusinessDataExport.EmployeeExport(
                id = stylist.id,
                role = BusinessDataExport.Role.EMPLOYEE,
                name = "Ivan",
                lastName = "Petrenko",
                phone = "+380509998877",
                email = "ivan@example.com",
                joinedAt = Instant.fromEpochMilliseconds(500),
                suspendedAt = suspendedAt,
                serviceIds = listOf(fixture.haircut.id),
                workingHours = listOf(WorkingHoursExport("MONDAY", LocalTime(9, 0), LocalTime(18, 0))),
                daysOff = listOf(DayOffExport(LocalDate(2026, 12, 31), LocalDate(2027, 1, 1))),
                permissions = exported.permissions,
            ),
            exported,
        )
        assertEquals(
            PermissionExport("CLIENTS", view = false, update = false, delete = false),
            exported.permissions.single {
                it.resource ==
                    "CLIENTS"
            },
        )
        assertEquals(
            PermissionExport("CLIENTS", view = true, update = true, delete = true),
            export.employees[0].permissions.single {
                it.resource ==
                    "CLIENTS"
            },
        )
    }

    @Test
    fun `should export the pending invitations by the employee who sent them, never their user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = Employee.stub(businessId = fixture.business.id, userId = fixture.ownerId)
        coEvery { fixture.employeeDataSource.getEmployees(fixture.business.id) } returns listOf(owner)
        val fromOwner = PendingInvitationExport(Uuid.random(), fixture.ownerId, Instant.fromEpochMilliseconds(1_000))
        val fromFormerEmployee =
            PendingInvitationExport(Uuid.random(), Uuid.random(), Instant.fromEpochMilliseconds(2_000))
        coEvery { fixture.exportDataSource.getPendingInvitations(fixture.business.id) } returns
            listOf(fromOwner, fromFormerEmployee)

        whenn()
        val export = fixture.export()

        then()
        assertEquals(
            listOf(
                BusinessDataExport.InvitationExport(
                    fromOwner.id,
                    invitedByEmployeeId = owner.id,
                    createdAt = fromOwner.createdAt,
                ),
                BusinessDataExport.InvitationExport(
                    fromFormerEmployee.id,
                    invitedByEmployeeId = null,
                    createdAt = fromFormerEmployee.createdAt,
                ),
            ),
            export.pendingInvitations,
        )
    }

    @Test
    fun `should export the clients and the appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(BusinessClientExport.stub(description = "allergic to latex", hasAccount = true))
        coEvery { fixture.exportDataSource.getClients(fixture.business.id) } returns clients

        whenn()
        val export = fixture.export()

        then()
        assertEquals(clients, export.clients)
        assertEquals(fixture.appointments, export.appointments)
    }

    @Test
    fun `should read the business's own data inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.export()

        then()
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail for a business that no longer exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessDataSource.getBusinessById(fixture.business.id) } returns null

        whenn()
        val result = runSuspendCatching { fixture.sut.assemble(fixture.business.id) }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should fail when appointments can't hand over the business's appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.exportBusinessAppointments(fixture.business.id) } returns
            Result.failure(IllegalStateException("appointments unreachable"))

        whenn()
        val result = runSuspendCatching { fixture.sut.assemble(fixture.business.id) }

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should tell the owner the business export is ready and until when`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expiresAt = Instant.fromEpochMilliseconds(5_000)

        whenn()
        fixture.sut.announceReady(fixture.business.id, expiresAt)

        then()
        assertEquals(
            listOf(Triple(fixture.business.id, fixture.ownerId, expiresAt)),
            fixture.sentEvents.map {
                Triple(it.businessId, it.ownerUserId, it.expiresAt)
            },
        )
    }

    @Test
    fun `should announce nothing for a business deleted meanwhile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessDataSource.getBusinessById(fixture.business.id) } returns null

        whenn()
        fixture.sut.announceReady(fixture.business.id, Instant.fromEpochMilliseconds(5_000))

        then()
        assertTrue(fixture.sentEvents.isEmpty())
    }
}
