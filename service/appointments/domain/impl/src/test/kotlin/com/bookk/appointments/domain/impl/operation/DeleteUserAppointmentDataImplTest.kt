package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import library.encryption.test.ShredRecordingSubjectKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeleteUserAppointmentDataImplTest {

    private class SutFixture {
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val writesInTransaction = mutableListOf<Boolean>()
        val sentEvents = mutableListOf<AppointmentEvent>()
        val sentInTransaction = mutableListOf<Boolean>()
        val subjectKeys = ShredRecordingSubjectKeys(transactionManager::isInTransaction)
        val sut = DeleteUserAppointmentDataImpl(
            deletedUserDataSource,
            appointmentDataSource,
            requestDataSource,
            appointmentPermissionDataSource,
            subscriptionDataSource,
            eventProducer,
            subjectKeys,
            transactionManager,
        )

        fun stubErasure(
            userId: Uuid,
            upcoming: List<Appointment> = emptyList(),
            appointmentClientIds: Set<Uuid> = emptySet(),
            requestClientIds: Set<Uuid> = emptySet(),
            ownedBusinessIds: List<Uuid> = emptyList(),
            ownedBusinessClientIds: Set<Uuid> = emptySet(),
        ) {
            transactionManager.mockTransaction()
            coEvery { deletedUserDataSource.markDeleted(userId) } coAnswers { recordWrite() }
            coEvery { appointmentDataSource.getUpcomingIdsOfUserForUpdate(userId, any()) } returns
                upcoming.map { it.id }
            coEvery { appointmentDataSource.getClientIdsOfUserForUpdate(userId) } returns appointmentClientIds
            coEvery { requestDataSource.getClientIdsOfUserForUpdate(userId) } returns requestClientIds
            coEvery { subscriptionDataSource.getBusinessIdsOwnedByForUpdate(userId) } returns ownedBusinessIds
            coEvery { subscriptionDataSource.getClientIdsForUpdate(ownedBusinessIds) } returns ownedBusinessClientIds
            coEvery { appointmentDataSource.getAllByIds(upcoming.map { it.id }) } returns upcoming
            coEvery { appointmentDataSource.cancelAll(any(), any()) } coAnswers { recordWrite() }
            coEvery { appointmentDataSource.anonymizeClientUser(userId) } coAnswers { recordWrite() }
            coEvery { appointmentDataSource.anonymizeEmployee(userId) } coAnswers { recordWrite() }
            coEvery { appointmentDataSource.unlinkInitiator(userId) } coAnswers { recordWrite() }
            coEvery { requestDataSource.deleteForUser(userId) } coAnswers { recordWrite() }
            coEvery { appointmentPermissionDataSource.deleteForUser(userId) } coAnswers { recordWrite() }
            coEvery { subscriptionDataSource.detachBusinesses(ownedBusinessIds) } coAnswers { recordWrite() }
            coEvery { eventProducer.send(any<AppointmentEvent>(), any()) } coAnswers {
                sentEvents += firstArg<AppointmentEvent>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        private suspend fun recordWrite() {
            writesInTransaction += transactionManager.isInTransaction()
        }

        fun employeeNotifications() = sentEvents.filterIsInstance<AppointmentEvent.CancelledForDeletedClient>()

        fun clientNotifications() = sentEvents.filterIsInstance<AppointmentEvent.Cancelled>()
    }

    private fun AppointmentEvent.CancelledForDeletedClient.withoutIdempotencyKey() = copy(idempotencyKey = "")

    private fun AppointmentEvent.Cancelled.withoutIdempotencyKey() = copy(idempotencyKey = "")

    private fun bookedBy(clientUserId: Uuid, businessId: Uuid) = Appointment.stub(
        initiatorUserId = clientUserId,
        businessId = businessId,
    )

    private fun assignedTo(employeeUserId: Uuid, businessId: Uuid) = Appointment.stub(businessId = businessId)
        .let { it.copy(employee = it.employee.copy(userId = employeeUserId)) }

    @Test
    fun `should erase the user as client, employee and initiator with requests and grants at once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubErasure(userId)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeClientUser(userId) }
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeEmployee(userId) }
        coVerify(exactly = 1) { fixture.appointmentDataSource.unlinkInitiator(userId) }
        coVerify(exactly = 1) { fixture.requestDataSource.deleteForUser(userId) }
        coVerify(exactly = 1) { fixture.appointmentPermissionDataSource.deleteForUser(userId) }
        assertTrue(fixture.writesInTransaction.isNotEmpty())
        assertTrue(fixture.writesInTransaction.all { it })
    }

    @Test
    fun `should record the deletion, take every lock, read, and only then write`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val ownedBusinessIds = listOf(Uuid.random())
        fixture.stubErasure(userId, ownedBusinessIds = ownedBusinessIds)

        whenn()
        fixture.sut(userId)

        then()
        coVerifyOrder {
            fixture.deletedUserDataSource.markDeleted(userId)
            fixture.appointmentDataSource.getUpcomingIdsOfUserForUpdate(userId, any())
            fixture.appointmentDataSource.getClientIdsOfUserForUpdate(userId)
            fixture.requestDataSource.getClientIdsOfUserForUpdate(userId)
            fixture.subscriptionDataSource.getBusinessIdsOwnedByForUpdate(userId)
            fixture.subscriptionDataSource.getClientIdsForUpdate(ownedBusinessIds)
            fixture.appointmentDataSource.getAllByIds(any())
            fixture.appointmentDataSource.anonymizeClientUser(userId)
            fixture.subscriptionDataSource.detachBusinesses(ownedBusinessIds)
        }
    }

    @Test
    fun `should look for the user's appointments that start from now on`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val startingAfter = mutableListOf<Instant>()
        fixture.stubErasure(userId)
        coEvery {
            fixture.appointmentDataSource.getUpcomingIdsOfUserForUpdate(userId, capture(startingAfter))
        } returns emptyList()
        val before = Clock.System.now()

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(startingAfter.single() in before..(before + 5.seconds))
    }

    @Test
    fun `should cancel the user's upcoming appointments with a reason for each role`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val asClient = bookedBy(userId, businessId)
        val asEmployee = assignedTo(userId, businessId)
        val own = bookedBy(userId, businessId).let { it.copy(employee = it.employee.copy(userId = userId)) }
        fixture.stubErasure(userId, upcoming = listOf(asClient, asEmployee, own))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(businessId) } returns BusinessSnapshot.stub()

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.appointmentDataSource.cancelAll(listOf(asClient.id, own.id), "User deleted their account")
        }
        coVerify(exactly = 1) {
            fixture.appointmentDataSource.cancelAll(listOf(asEmployee.id), "Employee deleted their account")
        }
    }

    @Test
    fun `should notify the employee of each appointment cancelled by its client's account deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val first = bookedBy(userId, business.id)
        val second = bookedBy(userId, business.id)
        fixture.stubErasure(userId, upcoming = listOf(first, second))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        val expected = listOf(first, second).map {
            AppointmentEvent.CancelledForDeletedClient(
                employeeUserId = it.employee.userId,
                from = it.date,
                to = it.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                idempotencyKey = "",
            )
        }
        assertEquals(expected, fixture.employeeNotifications().map { it.withoutIdempotencyKey() })
        assertTrue(fixture.clientNotifications().isEmpty())
        assertEquals(listOf(true, true), fixture.sentInTransaction)
    }

    @Test
    fun `should notify a registered client when its employee's deletion cancels an appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val appointment = assignedTo(userId, business.id)
        fixture.stubErasure(userId, upcoming = listOf(appointment))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        val expected = AppointmentEvent.Cancelled(
            clientUserId = appointment.client.userId,
            employeeUserId = userId,
            from = appointment.date,
            to = appointment.dateEnd,
            timeZone = business.timeZone,
            address = business.address,
            businessName = business.name,
            price = moneyFormatter.print(appointment.totalAmount),
            reason = "Employee deleted their account",
            idempotencyKey = "",
        )
        assertEquals(listOf(expected), fixture.clientNotifications().map { it.withoutIdempotencyKey() })
        assertTrue(fixture.employeeNotifications().isEmpty())
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should not notify an unregistered client when its employee's deletion cancels an appointment`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = BusinessSnapshot.stub()
        val appointment = assignedTo(userId, business.id).let { it.copy(client = it.client.copy(userId = null)) }
        fixture.stubErasure(userId, upcoming = listOf(appointment))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should notify nobody about an appointment the user booked with themselves as employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = BusinessSnapshot.stub()
        val own = bookedBy(userId, business.id).let { it.copy(employee = it.employee.copy(userId = userId)) }
        fixture.stubErasure(userId, upcoming = listOf(own))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should still erase the user when a cancelled appointment's business is no longer known`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val orphan = bookedBy(userId, Uuid.random())
        fixture.stubErasure(userId, upcoming = listOf(orphan))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(orphan.businessId) } returns null

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(fixture.sentEvents.isEmpty())
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeClientUser(userId) }
    }

    @Test
    fun `should send nothing when no upcoming appointment was cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubErasure(userId)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AppointmentEvent>(), any()) }
    }

    @Test
    fun `should fail so the event is retried when a write fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubErasure(userId)
        coEvery { fixture.appointmentDataSource.anonymizeClientUser(userId) } throws
            IllegalStateException("database down")

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should drop the businesses the user owned`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val ownedBusinessIds = listOf(Uuid.random(), Uuid.random())
        fixture.stubErasure(userId, ownedBusinessIds = ownedBusinessIds)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.subscriptionDataSource.detachBusinesses(ownedBusinessIds) }
    }

    @Test
    fun `should read the business of a cancelled appointment before dropping the owner's businesses`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val userId = Uuid.random()
            val business = BusinessSnapshot.stub(ownerUserId = userId)
            fixture.stubErasure(
                userId,
                upcoming = listOf(assignedTo(userId, business.id)),
                ownedBusinessIds = listOf(business.id),
            )
            coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

            whenn()
            val result = fixture.sut(userId)

            then()
            assertTrue(result.isSuccess)
            coVerifyOrder {
                fixture.subscriptionDataSource.getBusinessSnapshot(business.id)
                fixture.subscriptionDataSource.detachBusinesses(listOf(business.id))
            }
            assertEquals(1, fixture.clientNotifications().size)
        }

    @Test
    fun `should shred the user's key and the keys of every client erased with the account in the transaction`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val userId = Uuid.random()
            val appointmentClientIds = setOf(Uuid.random())
            val requestClientIds = setOf(Uuid.random())
            val ownedBusinessIds = listOf(Uuid.random())
            val ownedBusinessClientIds = setOf(Uuid.random(), Uuid.random())
            fixture.stubErasure(
                userId,
                appointmentClientIds = appointmentClientIds,
                requestClientIds = requestClientIds,
                ownedBusinessIds = ownedBusinessIds,
                ownedBusinessClientIds = ownedBusinessClientIds,
            )

            whenn()
            fixture.sut(userId)

            then()
            val expected = setOf(userId) + appointmentClientIds + requestClientIds + ownedBusinessClientIds
            assertEquals(expected, fixture.subjectKeys.shreddedInTransaction)
        }
}
