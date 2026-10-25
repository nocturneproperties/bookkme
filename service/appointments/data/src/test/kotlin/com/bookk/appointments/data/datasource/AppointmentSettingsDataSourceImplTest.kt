package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
import kotlin.uuid.Uuid

internal class AppointmentSettingsDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable, SettingsTable, WorkingHoursTable, DayOffsTable
        )
        val sut = AppointmentSettingsDataSourceImpl()
        val subscriptionSut = AppointmentSubscriptionDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup(schedule: Schedule = Schedule()) {
            val snapshot = BusinessSnapshot.stub(schedule = schedule)
            suspendTransaction { subscriptionSut.attachBusiness(snapshot) }
            businessId = snapshot.id
        }
    }

    @Test
    fun `should create settings and retrieve by business id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val settings = AppointmentSettings.stub(fixture.businessId)

        whenn()
        val created = suspendTransaction { fixture.sut.create(settings) }
        val found = suspendTransaction { fixture.sut.get(fixture.businessId) }

        then()
        assertNotNull(found)
        assertEquals(fixture.businessId, found!!.businessId)
        assertEquals(settings.inBetweenBreakInMinutes, created.inBetweenBreakInMinutes)
    }

    @Test
    fun `should return null when settings not found for business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.get(fixture.businessId) }

        then()
        assertNull(found)
    }

    @Test
    fun `should update settings and persist changes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(
                AppointmentSettingsUpdate.stub(
                    businessId = fixture.businessId,
                    inBetweenBreakInMinutes = 20,
                    appointmentNote = "Updated note"
                )
            )
        }

        then()
        assertEquals(20, updated.inBetweenBreakInMinutes)
        assertEquals("Updated note", updated.appointmentNote)
    }

    @Test
    fun `should enable automatic completion for new settings by default`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }

        then()
        assertTrue(suspendTransaction { fixture.sut.get(fixture.businessId) }!!.automaticCompletion)
    }

    @Test
    fun `should update automatic completion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(AppointmentSettingsUpdate.stub(businessId = fixture.businessId, automaticCompletion = false))
        }

        then()
        assertFalse(updated.automaticCompletion)
        assertFalse(suspendTransaction { fixture.sut.get(fixture.businessId) }!!.automaticCompletion)
    }

    @Test
    fun `should keep automatic completion when update omits it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.create(AppointmentSettings.stub(fixture.businessId).copy(automaticCompletion = false))
        }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(AppointmentSettingsUpdate.stub(businessId = fixture.businessId, automaticCompletion = null))
        }

        then()
        assertFalse(updated.automaticCompletion)
    }

    @Test
    fun `should lock the settings row while updating it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }
        val capturedSql = CapturingSqlLogger()

        whenn()
        suspendTransaction {
            addLogger(capturedSql)
            fixture.sut.update(AppointmentSettingsUpdate.stub(businessId = fixture.businessId))
        }

        then()
        val selects = capturedSql.statements.filter { it.startsWith("SELECT", ignoreCase = true) }
        assertTrue(selects.any { it.contains("FOR UPDATE", ignoreCase = true) })
    }

    @Test
    fun `should expose the business schedule on the settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(
                DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))
            ),
            dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        )
        val dayOffs = schedule.dayOffs
        fixture.setup(schedule = schedule)
        suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }

        whenn()
        val found = suspendTransaction { fixture.sut.get(fixture.businessId) }

        then()
        assertEquals(listOf(DayOfWeek.SATURDAY), found!!.schedule.activeDays())
        assertEquals(
            listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0))),
            found.schedule[DayOfWeek.SATURDAY].workingTime
        )
        assertEquals(dayOffs, found.schedule.dayOffs)
    }

    @Test
    fun `should keep the business schedule when updating settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.update(
                AppointmentSettingsUpdate.stub(businessId = fixture.businessId, appointmentNote = "Updated note")
            )
        }

        then()
        assertEquals(created.schedule.activeDays().sorted(), updated.schedule.activeDays().sorted())
        assertEquals(
            created.schedule[DayOfWeek.MONDAY].workingTime,
            updated.schedule[DayOfWeek.MONDAY].workingTime
        )
    }

    @Test
    fun `should retrieve settings for update with lock`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.create(AppointmentSettings.stub(fixture.businessId)) }

        whenn()
        val found = suspendTransaction { fixture.sut.getForUpdate(fixture.businessId) }

        then()
        assertNotNull(found)
        assertEquals(fixture.businessId, found!!.businessId)
    }

    @Test
    fun `should return null from getForUpdate when settings not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.getForUpdate(fixture.businessId) }

        then()
        assertNull(found)
    }
}
