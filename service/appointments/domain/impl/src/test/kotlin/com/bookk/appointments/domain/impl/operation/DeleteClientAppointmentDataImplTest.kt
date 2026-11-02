package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.appointments.client.api.event.ClientDeletionCause
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeleteClientAppointmentDataImplTest {

    private class SutFixture {
        val deletedClientDataSource = mockk<DeletedClientDataSource>()
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val subscriptionDataSource = mockk<AppointmentSubscriptionDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val writesInTransaction = mutableListOf<Boolean>()
        val sentEvents = mutableListOf<AppointmentEvent>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut = DeleteClientAppointmentDataImpl(
            deletedClientDataSource,
            appointmentDataSource,
            requestDataSource,
            subscriptionDataSource,
            eventProducer,
            transactionManager
        )

        fun stubWrites(businessId: Uuid, clientId: Uuid, cancelled: List<Appointment> = emptyList()) {
            transactionManager.mockTransaction()
            coEvery { deletedClientDataSource.markDeleted(businessId, clientId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { appointmentDataSource.cancelUpcomingForClient(businessId, clientId, any(), any()) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
                cancelled
            }
            coEvery { appointmentDataSource.anonymizeClient(businessId, clientId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { requestDataSource.deleteForClient(businessId, clientId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { eventProducer.send(any<AppointmentEvent>(), any()) } coAnswers {
                sentEvents += firstArg<AppointmentEvent>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        fun employeeNotifications() = sentEvents.filterIsInstance<AppointmentEvent.CancelledForDeletedClient>()

        fun clientNotifications() = sentEvents.filterIsInstance<AppointmentEvent.Cancelled>()
    }

    private fun AppointmentEvent.CancelledForDeletedClient.withoutIdempotencyKey() = copy(idempotencyKey = "")

    private fun AppointmentEvent.Cancelled.withoutIdempotencyKey() = copy(idempotencyKey = "")

    private fun Appointment.cancelledForRemoval() =
        copy(cancellationReason = DeleteClientAppointmentDataImpl.CLIENT_REMOVED_CANCELLATION_REASON)

    @Test
    fun `should erase the client from the business's appointments and requests in one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        fixture.stubWrites(businessId, clientId)

        whenn()
        val result = fixture.sut(businessId, clientId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeClient(businessId, clientId) }
        coVerify(exactly = 1) { fixture.requestDataSource.deleteForClient(businessId, clientId) }
        assertEquals(listOf(true, true, true, true), fixture.writesInTransaction)
    }

    @Test
    fun `should record the deletion first, then lock and cancel the client's upcoming appointments before anonymizing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        fixture.stubWrites(businessId, clientId)

        whenn()
        fixture.sut(businessId, clientId)

        then()
        coVerifyOrder {
            fixture.deletedClientDataSource.markDeleted(businessId, clientId)
            fixture.appointmentDataSource.cancelUpcomingForClient(businessId, clientId, any(), any())
            fixture.appointmentDataSource.anonymizeClient(businessId, clientId)
            fixture.requestDataSource.deleteForClient(businessId, clientId)
        }
    }

    @Test
    fun `should cancel the client's upcoming appointments with the removal reason`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val startingAfter = mutableListOf<Instant>()
        fixture.stubWrites(businessId, clientId)
        coEvery {
            fixture.appointmentDataSource.cancelUpcomingForClient(businessId, clientId, capture(startingAfter), any())
        } returns emptyList()
        val before = Clock.System.now()

        whenn()
        val result = fixture.sut(businessId, clientId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.appointmentDataSource.cancelUpcomingForClient(businessId, clientId, any(), "Client was removed by the business")
        }
        assertTrue(startingAfter.single() in before..(before + 5.seconds))
    }

    @Test
    fun `should tell the assigned employee that the business removed the client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val business = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val clientId = Uuid.random()
        val first = Appointment.stub(businessId = business.id).cancelledForRemoval()
        val second = Appointment.stub(businessId = business.id).cancelledForRemoval()
        fixture.stubWrites(business.id, clientId, cancelled = listOf(first, second))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(business.id, clientId)

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
                cause = ClientDeletionCause.REMOVED_BY_BUSINESS
            )
        }
        assertEquals(expected, fixture.employeeNotifications().map { it.withoutIdempotencyKey() })
        assertTrue(fixture.sentInTransaction.all { it })
    }

    @Test
    fun `should tell a client with an account that their appointment was cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val business = BusinessSnapshot.stub()
        val clientId = Uuid.random()
        val appointment = Appointment.stub(businessId = business.id)
            .let { it.copy(client = it.client.copy(id = clientId, userId = Uuid.random())) }
            .cancelledForRemoval()
        fixture.stubWrites(business.id, clientId, cancelled = listOf(appointment))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(business.id, clientId)

        then()
        assertTrue(result.isSuccess)
        val expected = AppointmentEvent.Cancelled(
            clientUserId = appointment.client.userId,
            clientName = appointment.client.fullName,
            employeeUserId = appointment.employee.userId,
            employeeName = appointment.employee.fullName,
            from = appointment.date,
            to = appointment.dateEnd,
            timeZone = business.timeZone,
            address = business.address,
            businessName = business.name,
            price = moneyFormatter.print(appointment.totalAmount),
            reason = "Client was removed by the business",
            idempotencyKey = ""
        )
        assertEquals(listOf(expected), fixture.clientNotifications().map { it.withoutIdempotencyKey() })
    }

    @Test
    fun `should not notify a client without an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val business = BusinessSnapshot.stub()
        val clientId = Uuid.random()
        val appointment = Appointment.stub(businessId = business.id)
            .let { it.copy(client = it.client.copy(id = clientId, userId = null)) }
            .cancelledForRemoval()
        fixture.stubWrites(business.id, clientId, cancelled = listOf(appointment))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        fixture.sut(business.id, clientId)

        then()
        assertTrue(fixture.clientNotifications().isEmpty())
        assertEquals(1, fixture.employeeNotifications().size)
    }

    @Test
    fun `should tell an employee who is the client only as the client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val business = BusinessSnapshot.stub()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        val ownAppointment = Appointment.stub(businessId = business.id)
            .let { it.copy(client = it.client.copy(id = clientId, userId = userId), employee = it.employee.copy(userId = userId)) }
            .cancelledForRemoval()
        fixture.stubWrites(business.id, clientId, cancelled = listOf(ownAppointment))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        fixture.sut(business.id, clientId)

        then()
        assertTrue(fixture.employeeNotifications().isEmpty())
        assertEquals(listOf(userId), fixture.clientNotifications().map { it.clientUserId })
    }

    @Test
    fun `should still erase the client when the business is no longer known`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        fixture.stubWrites(businessId, clientId, cancelled = listOf(Appointment.stub(businessId = businessId)))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(businessId) } returns null

        whenn()
        val result = fixture.sut(businessId, clientId)

        then()
        assertTrue(result.isSuccess)
        assertTrue(fixture.sentEvents.isEmpty())
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeClient(businessId, clientId) }
    }

    @Test
    fun `should send nothing when no upcoming appointment was cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        fixture.stubWrites(businessId, clientId)

        whenn()
        val result = fixture.sut(businessId, clientId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AppointmentEvent>(), any()) }
    }

    @Test
    fun `should fail so the event is retried when a write fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        fixture.stubWrites(businessId, clientId)
        coEvery { fixture.appointmentDataSource.anonymizeClient(businessId, clientId) } throws IllegalStateException("database down")

        whenn()
        val result = fixture.sut(businessId, clientId)

        then()
        assertTrue(result.isFailure)
    }
}
