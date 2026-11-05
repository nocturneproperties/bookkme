package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable,
            WorkingHoursTable,
            DayOffsTable,
            SettingsTable,
            AppointmentTable,
            AppointmentServicesTable,
            PriceAdjustmentTable,
            PriceAdjustmentServicesTable,
        )
        val sut = AppointmentDataSourceImpl()
        val subscriptionSut = AppointmentSubscriptionDataSourceImpl()
        val settingsSut = AppointmentSettingsDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup(automaticCompletion: Boolean = true, timeZone: TimeZone = TimeZone.UTC) {
            businessId = attachBusiness(automaticCompletion, timeZone)
        }

        suspend fun attachBusiness(automaticCompletion: Boolean, timeZone: TimeZone = TimeZone.UTC): Uuid {
            val snapshot = BusinessSnapshot.stub(timeZone = timeZone)
            suspendTransaction {
                subscriptionSut.attachBusiness(snapshot)
                settingsSut.create(AppointmentSettings.stub(snapshot.id).copy(automaticCompletion = automaticCompletion))
            }
            return snapshot.id
        }

        fun buildRequest(
            userId: Uuid = Uuid.random(),
            date: Instant = Instant.fromEpochMilliseconds(0),
            employee: EmployeeSnapshot = EmployeeSnapshot.stub(),
        ) = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = date, employee = employee)
    }

    @Test
    fun `should create appointment from request and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = fixture.buildRequest()

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found.id)
        assertEquals(fixture.businessId, found.businessId)
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
    }

    @Test
    fun `should retrieve appointment for update by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val found = suspendTransaction { fixture.sut.getForUpdate(created.id) }

        then()
        assertEquals(created.id, found.id)
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
    }

    @Test
    fun `should lock the appointment row when retrieving it for update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val capturedSql = CapturingSqlLogger()

        whenn()
        suspendTransaction {
            addLogger(capturedSql)
            fixture.sut.getForUpdate(created.id)
        }

        then()
        val selects = capturedSql.statements.filter { it.startsWith("SELECT", ignoreCase = true) }
        assertTrue(selects.any { it.contains("FOR UPDATE", ignoreCase = true) })
    }

    @Test
    fun `should throw not found when retrieving missing appointment for update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.getForUpdate(Uuid.random()) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should create appointment from appointment entity`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val appointment = Appointment.stub(businessId = fixture.businessId)

        whenn()
        val created = suspendTransaction { fixture.sut.create(appointment) }

        then()
        assertNotNull(created)
        assertEquals(fixture.businessId, created.businessId)
    }

    @Test
    fun `should retrieve all appointments for business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val all = suspendTransaction { fixture.sut.getAll(fixture.businessId) }

        then()
        assertEquals(2, all.size)
    }

    @Test
    fun `should return empty list when no appointments exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val all = suspendTransaction { fixture.sut.getAll(fixture.businessId) }

        then()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `should update appointment note`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val updated = suspendTransaction { fixture.sut.update(created.copy(note = "Updated note")) }

        then()
        assertEquals("Updated note", updated.note)
    }

    @Test
    fun `should ignore status when updating appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(created.copy(note = "Updated note", status = AppointmentStatus.COMPLETED))
        }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, updated.status)
        assertEquals("Updated note", updated.note)
        assertEquals(AppointmentStatus.SCHEDULED, suspendTransaction { fixture.sut.get(created.id) }.status)
    }

    @Test
    fun `should return the stored services after updating appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val newServices = listOf(ServiceSnapshot.stub(), ServiceSnapshot.stub())

        whenn()
        val updated = suspendTransaction { fixture.sut.update(created.copy(services = newServices)) }

        then()
        assertEquals(newServices.map { it.id }.toSet(), updated.services.map { it.id }.toSet())
        assertEquals(newServices.map { it.id }.toSet(), suspendTransaction { fixture.sut.get(created.id) }.services.map { it.id }.toSet())
    }

    @Test
    fun `should return the latest services when updating twice in one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val latestServices = listOf(ServiceSnapshot.stub(), ServiceSnapshot.stub())

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(created.copy(services = listOf(ServiceSnapshot.stub())))
            fixture.sut.update(created.copy(services = latestServices))
        }

        then()
        assertEquals(latestServices.map { it.id }.toSet(), updated.services.map { it.id }.toSet())
    }

    @Test
    fun `should throw not found when updating missing appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.update(Appointment.stub(businessId = fixture.businessId)) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should create appointment at the start of a cancelled one for the same client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val cancelled = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }
        suspendTransaction { fixture.sut.cancel(cancelled.id, "Reason") }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) } }

        then()
        assertTrue(result.isSuccess)
        assertEquals(AppointmentStatus.SCHEDULED, result.getOrThrow().status)
    }

    @Test
    fun `should reschedule appointment to the start of a cancelled one for the same client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val cancelledStart = Instant.fromEpochMilliseconds(0)
        val cancelled = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = cancelledStart)) }
        suspendTransaction { fixture.sut.cancel(cancelled.id, "Reason") }
        val scheduled = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = cancelledStart + 2.hours)) }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.update(scheduled.copy(date = cancelledStart)) } }

        then()
        assertTrue(result.isSuccess)
        assertEquals(cancelledStart, suspendTransaction { fixture.sut.get(scheduled.id) }.date)
    }

    @Test
    fun `should cancel appointment with reason`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val cancelled = suspendTransaction { fixture.sut.cancel(created.id, "Client no-show") }

        then()
        assertEquals(AppointmentStatus.CANCELLED, cancelled.status)
        assertEquals("Client no-show", cancelled.cancellationReason)
    }

    @Test
    fun `should delete appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.delete(created.id) }

        then()
        assertTrue(suspendTransaction { fixture.sut.getAll(fixture.businessId) }.isEmpty())
    }

    @Test
    fun `should detect overlapping appointments for same user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val baseDate = Clock.System.now()
        suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = baseDate)) }

        whenn()
        val overlapping = fixture.buildRequest(userId = userId, date = baseDate + 15.minutes)
        val hasOverlap = suspendTransaction { fixture.sut.hasOverlapsWith(overlapping) }

        then()
        assertTrue(hasOverlap)
    }

    @Test
    fun `should not detect overlap when appointments are far apart`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val baseDate = Clock.System.now()
        suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = baseDate)) }

        whenn()
        val nonOverlapping = fixture.buildRequest(userId = userId, date = baseDate + 2.hours)
        val hasOverlap = suspendTransaction { fixture.sut.hasOverlapsWith(nonOverlapping) }

        then()
        assertFalse(hasOverlap)
    }

    @Test
    fun `should retrieve appointments for date range`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val baseDate = Instant.fromEpochMilliseconds(1_000_000_000_000L)
        suspendTransaction { fixture.sut.create(fixture.buildRequest(date = baseDate)) }

        whenn()
        val range = baseDate..(baseDate + 1.hours)
        val found = suspendTransaction { fixture.sut.getAllForDate(fixture.businessId, range, employeeId = null) }

        then()
        assertEquals(1, found.size)
    }

    @Test
    fun `should retrieve only appointments of given employee for date range`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val baseDate = Instant.fromEpochMilliseconds(1_000_000_000_000L)
        val employee = EmployeeSnapshot.stub()
        val employeeAppointment = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = baseDate, employee = employee)) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest(date = baseDate + 30.minutes)) }

        whenn()
        val range = baseDate..(baseDate + 2.hours)
        val found = suspendTransaction { fixture.sut.getAllForDate(fixture.businessId, range, employeeId = employee.id) }

        then()
        assertEquals(listOf(employeeAppointment.id), found.map { it.id })
    }

    @Test
    fun `should return all appointments with pagination metadata`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val pagination = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, null) }

        then()
        assertEquals(3, pagination.data.size)
        assertEquals(3L, pagination.metadata.total)
        assertEquals(1L, pagination.metadata.page)
        assertEquals(10, pagination.metadata.pageSize)
    }

    @Test
    fun `should filter paginated appointments by client name query`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        // ClientSnapshot.stub() uses fullName "Client Name"
        val pagination = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "client") }

        then()
        assertEquals(2, pagination.data.size)
    }

    @Test
    fun `should filter paginated appointments by service name query`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        // ServiceSnapshot.stub() uses name "Service Name"
        val pagination = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "service") }

        then()
        assertEquals(1, pagination.data.size)
    }

    @Test
    fun `should keep paginated query results within the requested business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val own = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(AppointmentRequest.stub(businessId = otherBusinessId, date = Instant.fromEpochMilliseconds(0))) }

        whenn()
        val byService = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "service") }
        val byClient = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "client") }

        then()
        assertEquals(listOf(own.id), byService.data.map { it.id })
        assertEquals(1L, byService.metadata.total)
        assertEquals(listOf(own.id), byClient.data.map { it.id })
        assertEquals(1L, byClient.metadata.total)
    }

    @Test
    fun `should return empty list when paginated query matches nothing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val pagination = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, "xyz-nonexistent") }

        then()
        assertTrue(pagination.data.isEmpty())
        assertEquals(0L, pagination.metadata.total)
    }

    @Test
    fun `should paginate appointments using offset`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val page2 = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 2, 2, null) }

        then()
        assertEquals(1, page2.data.size)
        assertEquals(3L, page2.metadata.total)
        assertEquals(2L, page2.metadata.page)
    }

    @Test
    fun `should mark past scheduled appointments as completed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        // date at epoch → dateEnd = epoch + 30 min, well before now
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.fromEpochMilliseconds(0))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        val updated = suspendTransaction { fixture.sut.getAll(fixture.businessId) }
        assertEquals(AppointmentStatus.COMPLETED, updated.first { it.id == created.id }.status)
    }

    @Test
    fun `should record the system as the completer of automatically completed appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        assertEquals(AppointmentCompletedBy.SYSTEM, suspendTransaction { fixture.sut.get(created.id) }.completedBy)
    }

    @Test
    fun `should not mark past appointments as completed when the business disabled automatic completion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(automaticCompletion = false)
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should only mark appointments of businesses with automatic completion enabled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(automaticCompletion = true)
        val disabledBusinessId = fixture.attachBusiness(automaticCompletion = false)
        val enabledAppointment = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val disabledAppointment = suspendTransaction {
            fixture.sut.create(AppointmentRequest.stub(businessId = disabledBusinessId, date = Instant.fromEpochMilliseconds(0)))
        }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        assertEquals(AppointmentStatus.COMPLETED, suspendTransaction { fixture.sut.get(enabledAppointment.id) }.status)
        assertEquals(AppointmentStatus.SCHEDULED, suspendTransaction { fixture.sut.get(disabledAppointment.id) }.status)
    }

    @Test
    fun `should not create appointments as completed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        then()
        assertNull(suspendTransaction { fixture.sut.get(created.id) }.completedBy)
    }

    @Test
    fun `should mark appointment as completed by user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(automaticCompletion = false)
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val completed = suspendTransaction { fixture.sut.markCompletedByUser(created.id) }

        then()
        assertEquals(AppointmentStatus.COMPLETED, completed.status)
        assertEquals(AppointmentCompletedBy.USER, completed.completedBy)
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.COMPLETED, found.status)
        assertEquals(AppointmentCompletedBy.USER, found.completedBy)
    }

    @Test
    fun `should throw not found when user completes missing appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.markCompletedByUser(Uuid.random()) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should not complete appointments ending exactly at the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(created.dateEnd) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should not mark future appointments as completed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val futureDate = Clock.System.now() + 24.hours
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = futureDate)) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        val all = suspendTransaction { fixture.sut.getAll(fixture.businessId) }
        assertEquals(AppointmentStatus.SCHEDULED, all.first { it.id == created.id }.status)
    }

    @Test
    fun `should not mark no-show appointments as completed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.markNoShow(created.id) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        assertEquals(AppointmentStatus.NO_SHOW, suspendTransaction { fixture.sut.get(created.id) }.status)
    }

    @Test
    fun `should mark appointment as no-show`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val marked = suspendTransaction { fixture.sut.markNoShow(created.id) }

        then()
        assertEquals(AppointmentStatus.NO_SHOW, marked.status)
        assertEquals(AppointmentStatus.NO_SHOW, suspendTransaction { fixture.sut.get(created.id) }.status)
    }

    @Test
    fun `should clear the completer when marking completed appointment as no-show`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        whenn()
        val marked = suspendTransaction { fixture.sut.markNoShow(created.id) }

        then()
        assertEquals(AppointmentStatus.NO_SHOW, marked.status)
        assertNull(marked.completedBy)
    }

    @Test
    fun `should throw not found when marking missing appointment as no-show`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.markNoShow(Uuid.random()) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should keep the client's user id on an appointment created from a request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(userId = clientUserId)) }

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(clientUserId, found.client.userId)
    }

    @Test
    fun `should keep no client user id for a client without an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(userId = null)) }

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNull(found.client.userId)
    }

    @Test
    fun `should clear the client's user id when anonymizing the client's account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(userId = clientUserId)) }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClientUser(clientUserId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNull(found.client.userId)
    }

    @Test
    fun `should anonymize client PII on appointments of the deleted client's account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val request = fixture.buildRequest().let {
            it.copy(client = it.client.copy(userId = clientUserId, fullName = "Alice", phone = "+123", email = "alice@test.com"))
        }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClientUser(clientUserId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("Deleted User", found.client.fullName)
        assertNull(found.client.phone)
        assertNull(found.client.email)
        assertEquals(created.client.id, found.client.id)
    }

    @Test
    fun `should keep client PII on appointments of other clients when anonymizing a client's account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = fixture.buildRequest().let {
            it.copy(client = it.client.copy(fullName = "Carol", phone = "+456", email = "carol@test.com"))
        }
        val untouched = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClientUser(Uuid.random()) }
        val found = suspendTransaction { fixture.sut.get(untouched.id) }

        then()
        assertEquals("Carol", found.client.fullName)
        assertEquals("+456", found.client.phone)
        assertEquals("carol@test.com", found.client.email)
    }

    @Test
    fun `should leave client details alone when anonymizing an employee user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let {
            it.copy(client = it.client.copy(userId = userId, fullName = "Dave"))
        }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeEmployee(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("Dave", found.client.fullName)
    }

    @Test
    fun `should cancel the client's scheduled appointments that start after the given time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val now = Clock.System.now()
        val request = fixture.buildRequest(date = now + 24.hours).let { it.copy(client = it.client.copy(userId = clientUserId)) }
        val upcoming = suspendTransaction { fixture.sut.create(request) }

        whenn()
        val cancelled = suspendTransaction { fixture.sut.cancelUpcomingForUser(clientUserId, now, "User deleted their account", "Employee deleted their account") }
        val found = suspendTransaction { fixture.sut.get(upcoming.id) }

        then()
        assertEquals(AppointmentStatus.CANCELLED, found.status)
        assertEquals("User deleted their account", found.cancellationReason)
        assertEquals(listOf(found), cancelled)
    }

    @Test
    fun `should keep the client's appointments that started before the given time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val now = Clock.System.now()
        val request = fixture.buildRequest(date = now - 24.hours).let { it.copy(client = it.client.copy(userId = clientUserId)) }
        val past = suspendTransaction { fixture.sut.create(request) }

        whenn()
        val cancelled = suspendTransaction { fixture.sut.cancelUpcomingForUser(clientUserId, now, "User deleted their account", "Employee deleted their account") }
        val found = suspendTransaction { fixture.sut.get(past.id) }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `should keep the client's upcoming appointments that are no longer scheduled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val now = Clock.System.now()
        val request = fixture.buildRequest(date = now + 24.hours).let { it.copy(client = it.client.copy(userId = clientUserId)) }
        val created = suspendTransaction { fixture.sut.create(request) }
        suspendTransaction { fixture.sut.cancel(created.id, "Business closed") }

        whenn()
        val cancelled = suspendTransaction { fixture.sut.cancelUpcomingForUser(clientUserId, now, "User deleted their account", "Employee deleted their account") }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("Business closed", found.cancellationReason)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `should keep upcoming appointments of other users when cancelling a user's`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val now = Clock.System.now()
        val other = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = now + 24.hours)) }

        whenn()
        suspendTransaction { fixture.sut.cancelUpcomingForUser(Uuid.random(), now, "User deleted their account", "Employee deleted their account") }
        val found = suspendTransaction { fixture.sut.get(other.id) }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
    }

    @Test
    fun `should cancel the employee's scheduled appointments that start after the given time with the employee reason`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employeeUserId = Uuid.random()
        val now = Clock.System.now()
        val upcoming = suspendTransaction {
            fixture.sut.create(fixture.buildRequest(date = now + 24.hours, employee = EmployeeSnapshot.stub(userId = employeeUserId)))
        }
        val past = suspendTransaction {
            fixture.sut.create(fixture.buildRequest(date = now - 24.hours, employee = EmployeeSnapshot.stub(userId = employeeUserId)))
        }

        whenn()
        val cancelled = suspendTransaction {
            fixture.sut.cancelUpcomingForUser(employeeUserId, now, "User deleted their account", "Employee deleted their account")
        }
        val found = suspendTransaction { fixture.sut.get(upcoming.id) }

        then()
        assertEquals(AppointmentStatus.CANCELLED, found.status)
        assertEquals("Employee deleted their account", found.cancellationReason)
        assertEquals(listOf(found), cancelled)
        assertEquals(AppointmentStatus.SCHEDULED, suspendTransaction { fixture.sut.get(past.id) }.status)
    }

    @Test
    fun `should cancel with the client reason an appointment the user booked with themselves as employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val now = Clock.System.now()
        val request = fixture.buildRequest(date = now + 24.hours, employee = EmployeeSnapshot.stub(userId = userId))
            .let { it.copy(client = it.client.copy(userId = userId)) }
        val own = suspendTransaction { fixture.sut.create(request) }

        whenn()
        val cancelled = suspendTransaction {
            fixture.sut.cancelUpcomingForUser(userId, now, "User deleted their account", "Employee deleted their account")
        }

        then()
        assertEquals(listOf(own.id), cancelled.map { it.id })
        assertEquals("User deleted their account", cancelled.single().cancellationReason)
    }

    @Test
    fun `should anonymize employee name on appointments assigned to the deleted user as employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let {
            it.copy(employee = it.employee.copy(userId = userId, fullName = "Bob"))
        }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeEmployee(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("Deleted User", found.employee.fullName)
    }

    @Test
    fun `should unlink the deleted user's account from appointments assigned to them as employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(employee = it.employee.copy(userId = userId)) }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeEmployee(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(Uuid.NIL, found.employee.userId)
        assertEquals(created.employee.id, found.employee.id)
    }

    @Test
    fun `should keep other employees' accounts when anonymizing an employee user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val untouched = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeEmployee(Uuid.random()) }
        val found = suspendTransaction { fixture.sut.get(untouched.id) }

        then()
        assertEquals(untouched.employee.userId, found.employee.userId)
        assertEquals(untouched.employee.fullName, found.employee.fullName)
    }

    @Test
    fun `should unlink the deleted user's account from appointments they booked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }

        whenn()
        suspendTransaction { fixture.sut.unlinkInitiator(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(Uuid.NIL, found.initiatorUserId)
    }

    @Test
    fun `should keep the initiator of appointments other users booked when unlinking an initiator`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val untouched = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.unlinkInitiator(Uuid.random()) }
        val found = suspendTransaction { fixture.sut.get(untouched.id) }

        then()
        assertEquals(untouched.initiatorUserId, found.initiatorUserId)
    }

    @Test
    fun `should not fail anonymizing appointments for an employee user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.anonymizeEmployee(Uuid.random()) } }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should attach price adjustment with additional services to appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val adjustment = PriceAdjustment.stub(
            additionalServices = listOf(ServiceSnapshot.stub().copy(price = Money.parse("USD 12.50"))),
            price = Money.parse("USD 99.99"),
            reason = "Extra wash",
        )

        whenn()
        val adjusted = suspendTransaction { fixture.sut.adjustPrice(created.id, adjustment) }

        then()
        assertEquals(adjustment, adjusted.priceAdjustment)
        assertEquals(adjustment, suspendTransaction { fixture.sut.get(created.id) }.priceAdjustment)
    }

    @Test
    fun `should store price adjustment without reason and additional services`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val adjustment = PriceAdjustment.stub(additionalServices = emptyList(), reason = null)

        whenn()
        suspendTransaction { fixture.sut.adjustPrice(created.id, adjustment) }

        then()
        assertEquals(adjustment, suspendTransaction { fixture.sut.get(created.id) }.priceAdjustment)
    }

    @Test
    fun `should replace previous price adjustment of appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.adjustPrice(created.id, PriceAdjustment.stub(additionalServices = listOf(ServiceSnapshot.stub(), ServiceSnapshot.stub()))) }
        val replacement = PriceAdjustment.stub(price = Money.parse("USD 10"), reason = "Corrected")

        whenn()
        val adjusted = suspendTransaction { fixture.sut.adjustPrice(created.id, replacement) }

        then()
        assertEquals(replacement, adjusted.priceAdjustment)
        assertEquals(replacement, suspendTransaction { fixture.sut.get(created.id) }.priceAdjustment)
    }

    @Test
    fun `should return price adjustment when completing and adjusting in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val adjustment = PriceAdjustment.stub()

        whenn()
        val adjusted = suspendTransaction {
            fixture.sut.markCompletedByUser(created.id)
            fixture.sut.adjustPrice(created.id, adjustment)
        }

        then()
        assertEquals(AppointmentStatus.COMPLETED, adjusted.status)
        assertEquals(adjustment, adjusted.priceAdjustment)
    }

    @Test
    fun `should not attach price adjustment to appointment on creation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        then()
        assertNull(suspendTransaction { fixture.sut.get(created.id) }.priceAdjustment)
    }

    @Test
    fun `should fail to adjust price of missing appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.adjustPrice(Uuid.random(), PriceAdjustment.stub()) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should include price adjustments in paginated appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val adjustedAppointment = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val plainAppointment = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val adjustment = PriceAdjustment.stub()
        suspendTransaction { fixture.sut.adjustPrice(adjustedAppointment.id, adjustment) }

        whenn()
        val pagination = suspendTransaction { fixture.sut.getAllPaginated(fixture.businessId, 10, 0, null) }

        then()
        assertEquals(adjustment, pagination.data.first { it.id == adjustedAppointment.id }.priceAdjustment)
        assertNull(pagination.data.first { it.id == plainAppointment.id }.priceAdjustment)
    }

    @Test
    fun `should delete appointment together with its price adjustment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.adjustPrice(created.id, PriceAdjustment.stub()) }

        whenn()
        suspendTransaction { fixture.sut.delete(created.id) }

        then()
        val result = runCatching { suspendTransaction { fixture.sut.get(created.id) } }
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should load all appointments with a constant number of queries`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val singleBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val severalBusinessId = fixture.attachBusiness(automaticCompletion = true)
        seedAdjustedAppointments(fixture, singleBusinessId, count = 1)
        seedAdjustedAppointments(fixture, severalBusinessId, count = 3)

        whenn()
        val singleQueries = countStatements { fixture.sut.getAll(singleBusinessId) }
        val severalQueries = countStatements { fixture.sut.getAll(severalBusinessId) }

        then()
        assertEquals(singleQueries, severalQueries)
    }

    @Test
    fun `should load appointments for a date with a constant number of queries`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val singleBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val severalBusinessId = fixture.attachBusiness(automaticCompletion = true)
        seedAdjustedAppointments(fixture, singleBusinessId, count = 1)
        seedAdjustedAppointments(fixture, severalBusinessId, count = 3)
        val range = Instant.fromEpochMilliseconds(0)..(Instant.fromEpochMilliseconds(0) + 1.hours)

        whenn()
        val singleQueries = countStatements { fixture.sut.getAllForDate(singleBusinessId, range, employeeId = null) }
        val severalQueries = countStatements { fixture.sut.getAllForDate(severalBusinessId, range, employeeId = null) }

        then()
        assertEquals(singleQueries, severalQueries)
    }

    @Test
    fun `should load paginated appointments with a constant number of queries`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val singleBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val severalBusinessId = fixture.attachBusiness(automaticCompletion = true)
        seedAdjustedAppointments(fixture, singleBusinessId, count = 1)
        seedAdjustedAppointments(fixture, severalBusinessId, count = 3)

        whenn()
        val singleQueries = countStatements { fixture.sut.getAllPaginated(singleBusinessId, 10, 0, null) }
        val severalQueries = countStatements { fixture.sut.getAllPaginated(severalBusinessId, 10, 0, null) }

        then()
        assertEquals(singleQueries, severalQueries)
    }

    private suspend fun seedAdjustedAppointments(fixture: SutFixture, businessId: Uuid, count: Int) = repeat(count) {
        val created = suspendTransaction {
            fixture.sut.create(AppointmentRequest.stub(businessId = businessId, date = Instant.fromEpochMilliseconds(0)))
        }
        suspendTransaction { fixture.sut.adjustPrice(created.id, PriceAdjustment.stub()) }
    }

    private suspend fun countStatements(read: suspend () -> Unit): Int = suspendTransaction {
        val before = statementCount
        read()
        statementCount - before
    }

    @Test
    fun `should give the client's appointments in the business the linked account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(id = clientId, userId = null)) }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.linkClientUser(fixture.businessId, clientId, userId) }

        then()
        assertEquals(userId, suspendTransaction { fixture.sut.get(created.id) }.client.userId)
    }

    @Test
    fun `should leave other clients and other businesses alone when linking an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val clientId = Uuid.random()
        val request = fixture.buildRequest()
        val otherClient = suspendTransaction { fixture.sut.create(request.copy(client = request.client.copy(userId = null))) }
        val otherBusiness = suspendTransaction {
            fixture.sut.create(request.copy(id = Uuid.random(), businessId = otherBusinessId, client = request.client.copy(id = clientId, userId = null)))
        }

        whenn()
        suspendTransaction { fixture.sut.linkClientUser(fixture.businessId, clientId, Uuid.random()) }

        then()
        assertNull(suspendTransaction { fixture.sut.get(otherClient.id) }.client.userId)
        assertNull(suspendTransaction { fixture.sut.get(otherBusiness.id) }.client.userId)
    }

    @Test
    fun `should move the merged client's appointments in the business to the remaining client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val fromClientId = Uuid.random()
        val toClientId = Uuid.random()
        val request = fixture.buildRequest()
        val merged = suspendTransaction { fixture.sut.create(request.copy(client = request.client.copy(id = fromClientId))) }
        val unrelated = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val otherBusiness = suspendTransaction {
            fixture.sut.create(request.copy(id = Uuid.random(), businessId = otherBusinessId, client = request.client.copy(id = fromClientId)))
        }

        whenn()
        suspendTransaction { fixture.sut.reassignClient(fixture.businessId, fromClientId, toClientId) }

        then()
        assertEquals(toClientId, suspendTransaction { fixture.sut.get(merged.id) }.client.id)
        assertEquals(unrelated.client.id, suspendTransaction { fixture.sut.get(unrelated.id) }.client.id)
        assertEquals(fromClientId, suspendTransaction { fixture.sut.get(otherBusiness.id) }.client.id)
    }

    @Test
    fun `should cancel the deleted client's scheduled appointments that start after the given time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val now = Clock.System.now()
        val upcoming = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = now + 24.hours)) }

        whenn()
        val cancelled = suspendTransaction {
            fixture.sut.cancelUpcomingForClient(fixture.businessId, upcoming.client.id, now, "Client was removed by the business")
        }
        val found = suspendTransaction { fixture.sut.get(upcoming.id) }

        then()
        assertEquals(AppointmentStatus.CANCELLED, found.status)
        assertEquals("Client was removed by the business", found.cancellationReason)
        assertEquals(listOf(found), cancelled)
    }

    @Test
    fun `should keep the deleted client's appointments that started before the given time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val now = Clock.System.now()
        val past = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = now - 24.hours)) }

        whenn()
        val cancelled = suspendTransaction {
            fixture.sut.cancelUpcomingForClient(fixture.businessId, past.client.id, now, "Client was removed by the business")
        }
        val found = suspendTransaction { fixture.sut.get(past.id) }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `should keep the deleted client's upcoming appointments that are no longer scheduled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val now = Clock.System.now()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = now + 24.hours)) }
        suspendTransaction { fixture.sut.cancel(created.id, "Business closed") }

        whenn()
        val cancelled = suspendTransaction {
            fixture.sut.cancelUpcomingForClient(fixture.businessId, created.client.id, now, "Client was removed by the business")
        }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("Business closed", found.cancellationReason)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `should not cancel upcoming appointments of other clients or of another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val now = Clock.System.now()
        val deleted = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = now + 24.hours)) }
        val otherClient = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = now + 48.hours)) }
        val otherBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val otherBusiness = suspendTransaction {
            fixture.sut.create(fixture.buildRequest(date = now + 24.hours).copy(businessId = otherBusinessId, client = deleted.client))
        }

        whenn()
        suspendTransaction { fixture.sut.cancelUpcomingForClient(fixture.businessId, deleted.client.id, now, "Client was removed by the business") }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, suspendTransaction { fixture.sut.get(otherClient.id) }.status)
        assertEquals(AppointmentStatus.SCHEDULED, suspendTransaction { fixture.sut.get(otherBusiness.id) }.status)
    }

    @Test
    fun `should anonymize the deleted client on every one of their appointments and unlink their account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = fixture.buildRequest().let {
            it.copy(client = it.client.copy(userId = Uuid.random(), fullName = "Alice", phone = "+123", email = "alice@test.com"))
        }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClient(fixture.businessId, created.client.id) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("Deleted Client", found.client.fullName)
        assertNull(found.client.phone)
        assertNull(found.client.email)
        assertNull(found.client.userId)
        assertEquals(created.client.id, found.client.id)
    }

    @Test
    fun `should keep other clients and other businesses when anonymizing a deleted client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val deleted = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val otherClient = suspendTransaction {
            fixture.sut.create(fixture.buildRequest().let { it.copy(client = it.client.copy(fullName = "Carol")) })
        }
        val otherBusinessId = fixture.attachBusiness(automaticCompletion = true)
        val otherBusiness = suspendTransaction {
            fixture.sut.create(fixture.buildRequest().copy(businessId = otherBusinessId, client = deleted.client.copy(fullName = "Bob")))
        }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClient(fixture.businessId, deleted.client.id) }

        then()
        assertEquals("Carol", suspendTransaction { fixture.sut.get(otherClient.id) }.client.fullName)
        assertEquals("Bob", suspendTransaction { fixture.sut.get(otherBusiness.id) }.client.fullName)
    }

    @Test
    fun `should clear the note and client details and keep the cancellation and price adjustment reasons when anonymizing a deleted account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val created = suspendTransaction {
            val appointment = fixture.sut.create(
                fixture.buildRequest().let { it.copy(client = it.client.copy(userId = clientUserId, fullName = "Alice", phone = "+123", email = "alice@test.com"), note = "Allergic to latex") },
            )
            fixture.sut.cancel(appointment.id, "Alice is ill")
            fixture.sut.adjustPrice(appointment.id, PriceAdjustment.stub(reason = "Discount for a regular"))
        }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClientUser(clientUserId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("", found.note)
        assertEquals("Alice is ill", found.cancellationReason)
        assertNull(found.client.phone)
        assertNull(found.client.email)
        assertNull(found.client.userId)
        assertEquals("Discount for a regular", found.priceAdjustment?.reason)
    }

    @Test
    fun `should clear the note and client details and keep the cancellation and price adjustment reasons when anonymizing a deleted client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val created = suspendTransaction {
            val appointment = fixture.sut.create(
                fixture.buildRequest().let { it.copy(client = it.client.copy(userId = clientUserId, fullName = "Alice", phone = "+123", email = "alice@test.com"), note = "Allergic to latex") },
            )
            fixture.sut.cancel(appointment.id, "Alice is ill")
            fixture.sut.adjustPrice(appointment.id, PriceAdjustment.stub(reason = "Discount for a regular"))
        }

        whenn()
        suspendTransaction { fixture.sut.anonymizeClient(fixture.businessId, created.client.id) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals("", found.note)
        assertEquals("Alice is ill", found.cancellationReason)
        assertNull(found.client.phone)
        assertNull(found.client.email)
        assertNull(found.client.userId)
        assertEquals("Discount for a regular", found.priceAdjustment?.reason)
    }

    @Test
    fun `should keep appointments scheduled until their local end day has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("Europe/Warsaw"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-11-04T10:00:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-11-04T22:59:59Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should complete appointments at the next local midnight`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("Europe/Warsaw"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-11-04T10:00:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-11-04T23:00:00Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.COMPLETED, found.status)
        assertEquals(AppointmentCompletedBy.SYSTEM, found.completedBy)
    }

    @Test
    fun `should keep an appointment ending at midnight open for its new local day`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("Europe/Warsaw"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-11-04T22:30:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-11-04T23:00:00Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should use the ending day for appointments crossing local midnight`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("Europe/Warsaw"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-11-04T22:45:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-11-05T12:00:00Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should wait for midnight in a business west of UTC`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("America/Los_Angeles"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-11-04T18:00:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-11-05T07:59:59Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should respect the short local day when daylight saving starts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("Europe/Warsaw"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-03-29T01:00:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-03-29T22:00:00Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.COMPLETED, found.status)
        assertEquals(AppointmentCompletedBy.SYSTEM, found.completedBy)
    }

    @Test
    fun `should respect the long local day when daylight saving ends`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(timeZone = TimeZone.of("Europe/Warsaw"))
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.parse("2026-10-25T01:00:00Z"))) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-10-25T22:59:59Z")) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.SCHEDULED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should complete only businesses whose local end day has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val easternBusinessId = fixture.attachBusiness(true, TimeZone.of("Pacific/Kiritimati"))
        val date = Instant.parse("2026-01-01T08:00:00Z")
        val utcAppointment = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = date)) }
        val easternAppointment = suspendTransaction {
            fixture.sut.create(AppointmentRequest.stub(businessId = easternBusinessId, date = date))
        }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Instant.parse("2026-01-01T12:00:00Z")) }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, suspendTransaction { fixture.sut.get(utcAppointment.id) }.status)
        assertEquals(AppointmentStatus.COMPLETED, suspendTransaction { fixture.sut.get(easternAppointment.id) }.status)
    }

    @Test
    fun `should mark expired scheduled appointments unresolved regardless of automatic completion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val disabledBusinessId = fixture.attachBusiness(false)
        val enabled = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val disabled = suspendTransaction { fixture.sut.create(AppointmentRequest.stub(businessId = disabledBusinessId)) }

        whenn()
        suspendTransaction { fixture.sut.markUnresolved(enabled.dateEnd + 1.minutes) }

        then()
        for (id in listOf(enabled.id, disabled.id)) {
            val found = suspendTransaction { fixture.sut.get(id) }
            assertEquals(AppointmentStatus.UNRESOLVED, found.status)
            assertNull(found.completedBy)
        }
        assertEquals(AppointmentStatus.UNRESOLVED, suspendTransaction { fixture.sut.getAll(fixture.businessId) }.single().status)
    }

    @Test
    fun `should leave the unresolved cutoff editable and expire it only after the boundary`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup(automaticCompletion = false)
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.markUnresolved(created.dateEnd) }
        val atBoundary = suspendTransaction { fixture.sut.get(created.id) }
        suspendTransaction { fixture.sut.markUnresolved(created.dateEnd + 1.minutes) }
        suspendTransaction { fixture.sut.markUnresolved(created.dateEnd + 1.minutes) }

        then()
        assertEquals(AppointmentStatus.SCHEDULED, atBoundary.status)
        val expired = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.UNRESOLVED, expired.status)
        assertNull(expired.completedBy)
    }

    @Test
    fun `should preserve recorded outcomes when expiring scheduled appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val completed = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val cancelled = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val noShow = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction {
            fixture.sut.markCompletedByUser(completed.id)
            fixture.sut.cancel(cancelled.id, "Cancelled")
            fixture.sut.markNoShow(noShow.id)
        }

        whenn()
        suspendTransaction { fixture.sut.markUnresolved(Clock.System.now()) }
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        val confirmed = suspendTransaction { fixture.sut.get(completed.id) }
        assertEquals(AppointmentStatus.COMPLETED, confirmed.status)
        assertEquals(AppointmentCompletedBy.USER, confirmed.completedBy)
        assertEquals(AppointmentStatus.CANCELLED, suspendTransaction { fixture.sut.get(cancelled.id) }.status)
        assertEquals(AppointmentStatus.NO_SHOW, suspendTransaction { fixture.sut.get(noShow.id) }.status)
    }

    @Test
    fun `should preserve unresolved appointments when automatic completion runs`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.markUnresolved(Clock.System.now()) }

        whenn()
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        then()
        val found = suspendTransaction { fixture.sut.get(created.id) }
        assertEquals(AppointmentStatus.UNRESOLVED, found.status)
        assertNull(found.completedBy)
    }

    @Test
    fun `should clear completion attribution when cancelling a completed appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.markCompleted(Clock.System.now()) }

        whenn()
        val cancelled = suspendTransaction { fixture.sut.cancel(created.id, "Corrected") }

        then()
        assertEquals(AppointmentStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.completedBy)
        assertNull(suspendTransaction { fixture.sut.get(created.id) }.completedBy)
    }

    @Test
    fun `should reopen a system completed appointment when rescheduled and clear its old checkout`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction {
            fixture.sut.markCompleted(Clock.System.now())
            fixture.sut.adjustPrice(created.id, PriceAdjustment.stub())
        }
        val completed = suspendTransaction { fixture.sut.get(created.id) }
        val rescheduled = completed.copy(
            date = Clock.System.now() + 24.hours,
            status = AppointmentStatus.SCHEDULED,
            completedBy = null,
            priceAdjustment = null,
        )

        whenn()
        val updated = suspendTransaction {
            fixture.sut.getForUpdate(created.id)
            fixture.sut.update(rescheduled)
        }

        then()
        assertEquals(rescheduled, updated)
        assertEquals(rescheduled, suspendTransaction { fixture.sut.get(created.id) })
    }

    @Test
    fun `should preserve a completed outcome and checkout when only its note changes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val recorded = suspendTransaction {
            fixture.sut.markCompletedByUser(created.id)
            fixture.sut.adjustPrice(created.id, PriceAdjustment.stub())
        }

        whenn()
        val updated = suspendTransaction { fixture.sut.update(recorded.copy(note = "Corrected note")) }

        then()
        assertEquals(recorded.copy(note = "Corrected note"), updated)
        assertEquals(updated, suspendTransaction { fixture.sut.get(created.id) })
    }

    @Test
    fun `should preserve a no-show outcome and checkout when only its note changes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val recorded = suspendTransaction {
            fixture.sut.markNoShow(created.id)
            fixture.sut.adjustPrice(created.id, PriceAdjustment.stub())
        }

        whenn()
        val updated = suspendTransaction { fixture.sut.update(recorded.copy(note = "Corrected note")) }

        then()
        assertEquals(recorded.copy(note = "Corrected note"), updated)
        assertEquals(updated, suspendTransaction { fixture.sut.get(created.id) })
    }
}
