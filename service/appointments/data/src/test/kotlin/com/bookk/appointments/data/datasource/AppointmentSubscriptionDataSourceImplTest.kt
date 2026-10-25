package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
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

internal class AppointmentSubscriptionDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AppointmentBusinessTable, WorkingHoursTable, DayOffsTable)
        val sut = AppointmentSubscriptionDataSourceImpl()
    }

    private fun saturdaySchedule(dayOffs: List<DayOffRange> = emptyList()) = Schedule(
        workingDays = listOf(DayOfWeek.SATURDAY),
        workingHours = mapOf(
            DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))
        ),
        dayOffs = dayOffs
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
            found.schedule[DayOfWeek.SATURDAY].workingTime
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
            schedule = saturdaySchedule(dayOffs = listOf(futureDayOff))
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
                Instant.fromEpochMilliseconds(2000)
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
    fun `should detach business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val snapshot = BusinessSnapshot.stub()
        suspendTransaction { fixture.sut.attachBusiness(snapshot) }

        whenn()
        suspendTransaction { fixture.sut.detachBusiness(snapshot.id) }
        val found = suspendTransaction { fixture.sut.getBusinessSnapshot(snapshot.id) }

        then()
        assertNull(found)
    }
}
