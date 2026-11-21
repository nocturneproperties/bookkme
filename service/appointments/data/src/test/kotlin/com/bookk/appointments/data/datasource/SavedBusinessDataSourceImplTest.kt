package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.data.orm.table.SavedBusinessTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.SavedBusiness
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SavedBusinessDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable,
            WorkingHoursTable,
            DayOffsTable,
            AppointmentTable,
            AppointmentServicesTable,
            PriceAdjustmentTable,
            PriceAdjustmentServicesTable,
            SavedBusinessTable,
        )
        val sut = SavedBusinessDataSourceImpl()
        val subscriptionSut = AppointmentSubscriptionDataSourceImpl()
        val appointmentSut = AppointmentDataSourceImpl(testSnapshotCipher())
        val userId = Uuid.random()

        suspend fun attachBusiness(enabled: Boolean = true): Uuid {
            val snapshot = BusinessSnapshot.stub(isEnabled = enabled)
            suspendTransaction { subscriptionSut.attachBusiness(snapshot) }
            return snapshot.id
        }

        suspend fun save(businessId: Uuid, savedAt: Instant = SAVED_AT, userId: Uuid = this.userId) {
            suspendTransaction { sut.save(userId, businessId, savedAt) }
        }

        suspend fun visit(businessId: Uuid, date: Instant, clientUserId: Uuid = userId, completed: Boolean = true) {
            val request = AppointmentRequest.stub(businessId = businessId, date = date)
            suspendTransaction {
                val appointment = appointmentSut.create(
                    request.copy(client = request.client.copy(userId = clientUserId)),
                )
                if (completed) appointmentSut.markCompletedByUser(appointment.id)
            }
        }

        suspend fun savedBusinesses(userId: Uuid = this.userId): List<SavedBusiness> {
            return suspendTransaction { sut.getAll(userId) }
        }
    }

    @Test
    fun `should give a saved business with when it was saved and whether it takes appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.attachBusiness()

        whenn()
        fixture.save(businessId)

        then()
        assertEquals(
            listOf(SavedBusiness(businessId = businessId, enabled = true, savedAt = SAVED_AT, lastVisitAt = null)),
            fixture.savedBusinesses(),
        )
    }

    @Test
    fun `should give a saved business that stopped taking appointments as not enabled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.attachBusiness()
        fixture.save(businessId)

        whenn()
        suspendTransaction { fixture.subscriptionSut.disableBusiness(businessId) }

        then()
        assertFalse(fixture.savedBusinesses().single().enabled)
    }

    @Test
    fun `should give the most recently saved business first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val older = fixture.attachBusiness()
        val newer = fixture.attachBusiness()
        fixture.save(older, savedAt = SAVED_AT)
        fixture.save(newer, savedAt = SAVED_AT + 1.hours)

        whenn()
        val saved = fixture.savedBusinesses()

        then()
        assertEquals(listOf(newer, older), saved.map { it.businessId })
    }

    @Test
    fun `should give the start of the user's last completed appointment at each saved business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val visited = fixture.attachBusiness()
        val neverVisited = fixture.attachBusiness()
        fixture.save(visited)
        fixture.save(neverVisited, savedAt = SAVED_AT + 1.hours)
        fixture.visit(visited, date = Instant.fromEpochMilliseconds(10 * HOUR_MILLIS))
        fixture.visit(visited, date = Instant.fromEpochMilliseconds(20 * HOUR_MILLIS))
        fixture.visit(visited, date = Instant.fromEpochMilliseconds(30 * HOUR_MILLIS), completed = false)
        fixture.visit(visited, date = Instant.fromEpochMilliseconds(40 * HOUR_MILLIS), clientUserId = Uuid.random())
        fixture.visit(neverVisited, date = Instant.fromEpochMilliseconds(50 * HOUR_MILLIS), completed = false)

        whenn()
        val saved = fixture.savedBusinesses().associate { it.businessId to it.lastVisitAt }

        then()
        assertEquals(mapOf(visited to Instant.fromEpochMilliseconds(20 * HOUR_MILLIS), neverVisited to null), saved)
    }

    @Test
    fun `should give when the user saved each business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.attachBusiness()
        val second = fixture.attachBusiness()
        fixture.save(first, savedAt = SAVED_AT)
        fixture.save(second, savedAt = SAVED_AT + 1.hours)
        fixture.save(first, userId = Uuid.random())

        whenn()
        val savedAt = suspendTransaction { fixture.sut.getSavedAt(fixture.userId) }

        then()
        assertEquals(mapOf(first to SAVED_AT, second to SAVED_AT + 1.hours), savedAt)
    }

    @Test
    fun `should tell whether the user saved a business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val saved = fixture.attachBusiness()
        val notSaved = fixture.attachBusiness()
        fixture.save(saved)
        fixture.save(notSaved, userId = Uuid.random())

        whenn()
        val isSaved = suspendTransaction { fixture.sut.isSaved(fixture.userId, saved) }
        val isNotSaved = suspendTransaction { fixture.sut.isSaved(fixture.userId, notSaved) }

        then()
        assertTrue(isSaved)
        assertFalse(isNotSaved)
    }

    @Test
    fun `should count only the user's own saved businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.attachBusiness()
        val second = fixture.attachBusiness()
        fixture.save(first)
        fixture.save(second)
        fixture.save(first, userId = Uuid.random())

        whenn()
        val count = suspendTransaction { fixture.sut.count(fixture.userId) }

        then()
        assertEquals(2L, count)
    }

    @Test
    fun `should keep one saved business and its first time when it is saved again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.attachBusiness()
        fixture.save(businessId, savedAt = SAVED_AT)

        whenn()
        fixture.save(businessId, savedAt = SAVED_AT + 1.hours)

        then()
        assertEquals(listOf(SAVED_AT), fixture.savedBusinesses().map { it.savedAt })
    }

    @Test
    fun `should remove only the given business from the user's saved businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val removed = fixture.attachBusiness()
        val kept = fixture.attachBusiness()
        val otherUserId = Uuid.random()
        fixture.save(removed)
        fixture.save(kept)
        fixture.save(removed, userId = otherUserId)

        whenn()
        suspendTransaction { fixture.sut.remove(fixture.userId, removed) }

        then()
        assertEquals(listOf(kept), fixture.savedBusinesses().map { it.businessId })
        assertEquals(listOf(removed), fixture.savedBusinesses(otherUserId).map { it.businessId })
    }

    @Test
    fun `should not fail removing a business the user never saved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.attachBusiness()

        whenn()
        suspendTransaction { fixture.sut.remove(fixture.userId, businessId) }

        then()
        assertTrue(fixture.savedBusinesses().isEmpty())
    }

    @Test
    fun `should delete every saved business of a deleted user and keep other users'`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.attachBusiness()
        val otherUserId = Uuid.random()
        fixture.save(businessId)
        fixture.save(businessId, userId = otherUserId)

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(fixture.userId) }

        then()
        assertTrue(fixture.savedBusinesses().isEmpty())
        assertEquals(0L, suspendTransaction { fixture.sut.count(fixture.userId) })
        assertEquals(listOf(businessId), fixture.savedBusinesses(otherUserId).map { it.businessId })
    }

    @Test
    fun `should drop a business from every saved list when the business is detached`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val detached = fixture.attachBusiness()
        val kept = fixture.attachBusiness()
        fixture.save(detached)
        fixture.save(kept)

        whenn()
        suspendTransaction { fixture.subscriptionSut.detachBusinesses(listOf(detached)) }

        then()
        assertEquals(listOf(kept), fixture.savedBusinesses().map { it.businessId })
    }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        val SAVED_AT = Instant.fromEpochMilliseconds(1_000 * HOUR_MILLIS)
    }
}
