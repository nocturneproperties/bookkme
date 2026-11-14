package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.core.data.cache.test.InMemoryCacheClient
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
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentSubscriptionDataSourceImplTest {

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
            AppointmentRequestTable,
            AppointmentRequestServicesTable,
        )
        val sut = AppointmentSubscriptionDataSourceImpl()
        val appointmentSut = AppointmentDataSourceImpl()
        val requestSut = AppointmentRequestDataSourceImpl(InMemoryCacheClient())
        val settingsSut = AppointmentSettingsDataSourceImpl()

        suspend fun attachWithClients(snapshot: BusinessSnapshot): Set<Uuid> {
            return suspendTransaction {
                sut.attachBusiness(snapshot)
                settingsSut.create(AppointmentSettings.stub(snapshot.id))
                val appointment = appointmentSut.create(AppointmentRequest.stub(businessId = snapshot.id))
                val request = requestSut.create(AppointmentRequest.stub(businessId = snapshot.id))
                setOf(appointment.client.id, request.client.id)
            }
        }
    }

    private fun saturdaySchedule(dayOffs: List<DayOffRange> = emptyList()) = Schedule(
        workingDays = listOf(DayOfWeek.SATURDAY),
        workingHours = mapOf(
            DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0))),
        ),
        dayOffs = dayOffs,
    )

    private val futureDayOff = DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31))

    @Test
    fun `should attach business and retrieve snapshot`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()

        whenn()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }

        then()
        assertNotNull(found)
        assertEquals(snapshot.id, found!!.id)
        assertEquals(snapshot.name, found.name)
        assertTrue(found.isEnabled)
    }

    @Test
    fun `should attach business with its schedule`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub(schedule = saturdaySchedule(dayOffs = listOf(futureDayOff)))

        whenn()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }

        then()
        assertEquals(listOf(DayOfWeek.SATURDAY), found!!.schedule.activeDays())
        assertEquals(
            listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0))),
            found.schedule[DayOfWeek.SATURDAY].workingTime,
        )
        assertEquals(listOf(futureDayOff), found.schedule.dayOffs)
    }

    @Test
    fun `should return null snapshot for unknown business`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(BusinessSnapshot.stub().id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should update business information and schedule`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }
        val modified = snapshot.copy(
            name = "New Name",
            address = "New Address",
            timeZone = TimeZone.of("Europe/Kyiv"),
            schedule = saturdaySchedule(dayOffs = listOf(futureDayOff)),
        )

        whenn()
        suspendTransaction { fixture.sut.updateBusiness(modified, Instant.fromEpochMilliseconds(1000)) }
        val updated = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }

        then()
        assertEquals("New Name", updated!!.name)
        assertEquals("New Address", updated.address)
        assertEquals(TimeZone.of("Europe/Kyiv"), updated.timeZone)
        assertEquals(listOf(DayOfWeek.SATURDAY), updated.schedule.activeDays())
        assertEquals(listOf(futureDayOff), updated.schedule.dayOffs)
    }

    @Test
    fun `should ignore business update that is older than the applied one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }
        suspendTransaction {
            fixture.sut.updateBusiness(
                snapshot.copy(name = "Newest", schedule = saturdaySchedule()),
                Instant.fromEpochMilliseconds(2000),
            )
        }

        whenn()
        suspendTransaction {
            fixture.sut.updateBusiness(snapshot.copy(name = "Outdated"), Instant.fromEpochMilliseconds(1000))
        }
        val updated = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }

        then()
        assertEquals("Newest", updated!!.name)
        assertEquals(listOf(DayOfWeek.SATURDAY), updated.schedule.activeDays())
    }

    @Test
    fun `should not update unknown business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()

        whenn()
        suspendTransaction { fixture.sut.updateBusiness(snapshot, Instant.fromEpochMilliseconds(1000)) }

        then()
        assertNull(suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) })
    }

    @Test
    fun `should enable and disable business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }

        whenn()
        suspendTransaction { fixture.sut.disableBusiness(snapshot.id) }
        val disabledCheck = suspendTransaction { fixture.sut.isBusinessEnabled(snapshot.id) }

        suspendTransaction { fixture.sut.enableBusiness(snapshot.id) }
        val enabledCheck = suspendTransaction { fixture.sut.isBusinessEnabled(snapshot.id) }

        then()
        assertFalse(disabledCheck)
        assertTrue(enabledCheck)
    }

    @Test
    fun `should return false for unknown business enabled check`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()

        whenn()
        val enabled = suspendTransaction { fixture.sut.isBusinessEnabled(snapshot.id) }

        then()
        assertFalse(enabled)
    }

    @Test
    fun `should give the ids of the owner's businesses only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerUserId = Uuid.random()
        val first = BusinessSnapshot.stub(ownerUserId = ownerUserId)
        val second = BusinessSnapshot.stub(ownerUserId = ownerUserId)
        suspendTransaction { listOf(first, second, BusinessSnapshot.stub()).forEach { fixture.sut.attachBusiness(it) } }

        whenn()
        val found = suspendTransaction { fixture.sut.getBusinessIdsOwnedByForUpdate(ownerUserId) }

        then()
        assertEquals(setOf(first.id, second.id), found.toSet())
    }

    @Test
    fun `should give the client ids of the given businesses' appointments and requests only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = BusinessSnapshot.stub()
        val second = BusinessSnapshot.stub()
        val firstClientIds = fixture.attachWithClients(first)
        val secondClientIds = fixture.attachWithClients(second)
        fixture.attachWithClients(BusinessSnapshot.stub())

        whenn()
        val found = suspendTransaction { fixture.sut.getClientIdsForUpdate(listOf(first.id, second.id)) }

        then()
        assertEquals(firstClientIds + secondClientIds, found)
    }

    @Test
    fun `should give no client ids for no businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.attachWithClients(BusinessSnapshot.stub())

        whenn()
        val found = suspendTransaction { fixture.sut.getClientIdsForUpdate(emptyList()) }

        then()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `should keep the business owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerUserId = Uuid.random()
        val snapshot = BusinessSnapshot.stub(ownerUserId = ownerUserId)

        whenn()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }

        then()
        assertEquals(ownerUserId, found!!.ownerUserId)
    }

    @Test
    fun `should move the business to a new owner with an update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        val newOwner = Uuid.random()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }

        whenn()
        suspendTransaction {
            fixture.sut.updateBusiness(snapshot.copy(ownerUserId = newOwner), Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertEquals(newOwner, suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }!!.ownerUserId)
    }

    @Test
    fun `should detach the given businesses and keep the others`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerUserId = Uuid.random()
        val first = BusinessSnapshot.stub(ownerUserId = ownerUserId)
        val second = BusinessSnapshot.stub(ownerUserId = ownerUserId)
        val other = BusinessSnapshot.stub()
        suspendTransaction { listOf(first, second, other).forEach { fixture.sut.attachBusiness(it) } }

        whenn()
        suspendTransaction { fixture.sut.detachBusinesses(listOf(first.id, second.id)) }

        then()
        assertNull(suspendTransaction { fixture.sut.getBusinessSnapshot(first.id) })
        assertNull(suspendTransaction { fixture.sut.getBusinessSnapshot(second.id) })
        assertNotNull(suspendTransaction { fixture.sut.getBusinessSnapshot(other.id) })
    }

    @Test
    fun `should keep the stored owner when an update carries none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }

        whenn()
        suspendTransaction {
            fixture.sut.updateBusiness(snapshot.copy(ownerUserId = null), Instant.fromEpochMilliseconds(1000))
        }

        then()
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }
        assertEquals(snapshot.ownerUserId, found!!.ownerUserId)
    }

    @Test
    fun `should list only the businesses whose owner is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val withoutOwner = BusinessSnapshot.stub(ownerUserId = null)
        val withOwner = BusinessSnapshot.stub()
        suspendTransaction { listOf(withoutOwner, withOwner).forEach { fixture.sut.attachBusiness(it) } }

        whenn()
        val missing = suspendTransaction { fixture.sut.getBusinessIdsWithoutOwner() }

        then()
        assertEquals(listOf(withoutOwner.id), missing)
    }

    @Test
    fun `should fill a missing owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub(ownerUserId = null)
        val ownerUserId = Uuid.random()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }

        whenn()
        suspendTransaction { fixture.sut.fillMissingOwner(snapshot.id, ownerUserId) }

        then()
        assertEquals(ownerUserId, suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }!!.ownerUserId)
    }

    @Test
    fun `should not replace an owner that is already set`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }

        whenn()
        suspendTransaction { fixture.sut.fillMissingOwner(snapshot.id, Uuid.random()) }

        then()
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }
        assertEquals(snapshot.ownerUserId, found!!.ownerUserId)
    }
}
