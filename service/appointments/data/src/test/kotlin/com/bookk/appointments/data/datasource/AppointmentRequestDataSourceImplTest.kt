package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
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

internal class AppointmentRequestDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            AppointmentBusinessTable, WorkingHoursTable, DayOffsTable, AppointmentRequestTable, AppointmentRequestServicesTable
        )
        val cacheClient = InMemoryCacheClient()
        val sut = AppointmentRequestDataSourceImpl(cacheClient)
        val subscriptionSut = AppointmentSubscriptionDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            val snapshot = BusinessSnapshot.stub()
            suspendTransaction { subscriptionSut.attachBusiness(snapshot) }
            businessId = snapshot.id
        }

        fun buildRequest(
            userId: Uuid = Uuid.random(),
            date: Instant = Instant.fromEpochMilliseconds(0)
        ) = AppointmentRequest.stub(initiatorUserId = userId, businessId = businessId, date = date)
    }

    @Test
    fun `should create request and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val request = fixture.buildRequest()

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
        assertEquals(AppointmentRequestStatus.PENDING, found.status)
    }

    @Test
    fun `should create request at the start of a declined one for the same client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val declined = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }
        suspendTransaction { fixture.sut.decline(declined.id, "Fully booked") }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) } }

        then()
        assertTrue(result.isSuccess)
        assertEquals(AppointmentRequestStatus.PENDING, result.getOrThrow().status)
    }

    @Test
    fun `should return null when request not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.get(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve only pending requests of the given user in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val otherBusiness = BusinessSnapshot.stub()
        suspendTransaction { fixture.subscriptionSut.attachBusiness(otherBusiness) }
        val first = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }
        val second = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }
        val approved = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }
        suspendTransaction { fixture.sut.approve(approved) }
        val declined = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }
        suspendTransaction { fixture.sut.decline(declined.id, "Fully booked") }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.create(AppointmentRequest.stub(initiatorUserId = userId, businessId = otherBusiness.id)) }

        whenn()
        val pending = suspendTransaction { fixture.sut.getPendingByUser(userId, fixture.businessId) }

        then()
        assertEquals(setOf(first.id, second.id), pending.map { it.id }.toSet())
    }

    @Test
    fun `should retrieve only pending requests`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val pending = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        suspendTransaction { fixture.sut.approve(pending) }
        suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val pending2 = suspendTransaction { fixture.sut.getPending(fixture.businessId) }

        then()
        assertEquals(1, pending2.size)
        assertEquals(AppointmentRequestStatus.PENDING, pending2.first().status)
    }

    @Test
    fun `should approve request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.approve(created) }
        val approved = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(AppointmentRequestStatus.APPROVED, approved?.status)
    }

    @Test
    fun `should decline request with reason`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val declined = suspendTransaction { fixture.sut.decline(created.id, "Fully booked") }

        then()
        assertEquals(AppointmentRequestStatus.DECLINED, declined.status)
        assertEquals("Fully booked", declined.declineReason)
    }

    @Test
    fun `should delete request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.delete(created) }

        then()
        assertNull(suspendTransaction { fixture.sut.get(created.id) })
    }

    @Test
    fun `should detect overlapping requests`() = runUnitTest {
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
    fun `should not detect overlap for distant requests`() = runUnitTest {
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
    fun `should not detect overlap with an approved request in the same slot`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val baseDate = Clock.System.now()
        val approved = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = baseDate)) }
        suspendTransaction { fixture.sut.approve(approved) }

        whenn()
        val sameSlot = fixture.buildRequest(userId = userId, date = baseDate)
        val hasOverlap = suspendTransaction { fixture.sut.hasOverlapsWith(sameSlot) }

        then()
        assertFalse(hasOverlap)
    }

    @Test
    fun `should not detect overlap with a declined request in the same slot`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val baseDate = Clock.System.now()
        val declined = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = baseDate)) }
        suspendTransaction { fixture.sut.decline(declined.id, "Cancelled by client") }

        whenn()
        val sameSlot = fixture.buildRequest(userId = userId, date = baseDate)
        val hasOverlap = suspendTransaction { fixture.sut.hasOverlapsWith(sameSlot) }

        then()
        assertFalse(hasOverlap)
    }

    @Test
    fun `should not detect overlap with a cancelled request in the same slot`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val outdatedSlot = Instant.fromEpochMilliseconds(0)
        suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId, date = outdatedSlot)) }
        suspendTransaction { fixture.sut.cancelOutdated(Clock.System.now()) }

        whenn()
        val sameSlot = fixture.buildRequest(userId = userId, date = outdatedSlot)
        val hasOverlap = suspendTransaction { fixture.sut.hasOverlapsWith(sameSlot) }

        then()
        assertFalse(hasOverlap)
    }

    @Test
    fun `should update request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val updated = suspendTransaction { fixture.sut.update(created.copy(note = "New note")) }

        then()
        assertEquals("New note", updated.note)
    }

    @Test
    fun `should cancel outdated pending requests before cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        // date at epoch → dateEnd = epoch + 30 min, well before now
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = Instant.fromEpochMilliseconds(0))) }

        whenn()
        suspendTransaction { fixture.sut.cancelOutdated(Clock.System.now()) }

        then()
        assertEquals(AppointmentRequestStatus.CANCELLED, suspendTransaction { fixture.sut.get(created.id) }?.status)
    }

    @Test
    fun `should not cancel future pending requests`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val futureDate = Clock.System.now() + 24.hours
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(date = futureDate)) }

        whenn()
        suspendTransaction { fixture.sut.cancelOutdated(Clock.System.now()) }

        then()
        assertEquals(AppointmentRequestStatus.PENDING, suspendTransaction { fixture.sut.get(created.id) }?.status)
    }

    @Test
    fun `should reserve an offer token that is not reserved yet`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val reserved = fixture.sut.reserveOfferToken("offer-token-abc")

        then()
        assertTrue(reserved)
    }

    @Test
    fun `should refuse to reserve an offer token that is already reserved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = "offer-token-abc"
        fixture.sut.reserveOfferToken(token)

        whenn()
        val reservedAgain = fixture.sut.reserveOfferToken(token)

        then()
        assertFalse(reservedAgain)
    }

    @Test
    fun `should reserve an offer token again after it is released`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val token = "offer-token-abc"
        fixture.sut.reserveOfferToken(token)

        whenn()
        fixture.sut.releaseOfferToken(token)

        then()
        assertTrue(fixture.sut.reserveOfferToken(token))
    }

    @Test
    fun `should delete requests the deleted user booked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest(userId = userId)) }

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should delete requests where the deleted user is the client, whoever booked them`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(userId = userId)) }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should delete requests assigned to the deleted user as employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let {
            it.copy(employee = it.employee.copy(userId = userId))
        }
        val created = suspendTransaction { fixture.sut.create(request) }

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(userId) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should not delete requests belonging to other users`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val untouched = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(userId) }
        val found = suspendTransaction { fixture.sut.get(untouched.id) }

        then()
        assertNotNull(found)
    }

    @Test
    fun `should not fail deleting requests for a user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.deleteForUser(Uuid.random()) } }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should keep the client's user id on a request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientUserId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(userId = clientUserId)) }

        whenn()
        val created = suspendTransaction { fixture.sut.create(request) }
        val found = suspendTransaction { fixture.sut.get(created.id) }

        then()
        assertEquals(clientUserId, found?.client?.userId)
    }

    @Test
    fun `should read request with a row lock`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val found = suspendTransaction { fixture.sut.getForUpdate(created.id) }

        then()
        assertEquals(created.id, found?.id)
        assertEquals(AppointmentRequestStatus.PENDING, found?.status)
    }

    @Test
    fun `should return null when locking a missing request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val found = suspendTransaction { fixture.sut.getForUpdate(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should decline a request locked in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }

        whenn()
        val declined = suspendTransaction {
            fixture.sut.getForUpdate(created.id)
            fixture.sut.decline(created.id, "Fully booked")
        }

        then()
        assertEquals(AppointmentRequestStatus.DECLINED, declined.status)
        assertEquals(AppointmentRequestStatus.DECLINED, suspendTransaction { fixture.sut.get(created.id) }?.status)
    }

    @Test
    fun `should give the client's requests in the business the linked account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        val request = fixture.buildRequest().let { it.copy(client = it.client.copy(id = clientId, userId = null)) }
        val created = suspendTransaction { fixture.sut.create(request) }
        val other = suspendTransaction { fixture.sut.create(fixture.buildRequest().let { it.copy(client = it.client.copy(userId = null)) }) }

        whenn()
        suspendTransaction { fixture.sut.linkClientUser(fixture.businessId, clientId, userId) }

        then()
        assertEquals(userId, suspendTransaction { fixture.sut.get(created.id) }?.client?.userId)
        assertNull(suspendTransaction { fixture.sut.get(other.id) }?.client?.userId)
    }

    @Test
    fun `should move the merged client's requests in the business to the remaining client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusiness = BusinessSnapshot.stub()
        suspendTransaction { fixture.subscriptionSut.attachBusiness(otherBusiness) }
        val fromClientId = Uuid.random()
        val toClientId = Uuid.random()
        val request = fixture.buildRequest()
        val merged = suspendTransaction { fixture.sut.create(request.copy(client = request.client.copy(id = fromClientId))) }
        val unrelated = suspendTransaction { fixture.sut.create(fixture.buildRequest()) }
        val inOtherBusiness = suspendTransaction {
            fixture.sut.create(fixture.buildRequest().copy(businessId = otherBusiness.id, client = request.client.copy(id = fromClientId)))
        }

        whenn()
        suspendTransaction { fixture.sut.reassignClient(fixture.businessId, fromClientId, toClientId) }

        then()
        assertEquals(toClientId, suspendTransaction { fixture.sut.get(merged.id) }?.client?.id)
        assertEquals(unrelated.client.id, suspendTransaction { fixture.sut.get(unrelated.id) }?.client?.id)
        assertEquals(fromClientId, suspendTransaction { fixture.sut.get(inOtherBusiness.id) }?.client?.id)
    }
}
