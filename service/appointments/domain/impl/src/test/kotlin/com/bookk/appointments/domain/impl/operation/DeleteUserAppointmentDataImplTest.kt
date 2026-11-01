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
import com.bookk.server.appointments.client.api.event.AppointmentEvent
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
        val sentEvents = mutableListOf<AppointmentEvent.CancelledForDeletedClient>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut = DeleteUserAppointmentDataImpl(
            deletedUserDataSource,
            appointmentDataSource,
            requestDataSource,
            appointmentPermissionDataSource,
            subscriptionDataSource,
            eventProducer,
            transactionManager
        )

        fun stubWrites(userId: Uuid, cancelled: List<Appointment> = emptyList()) {
            transactionManager.mockTransaction()
            coEvery { deletedUserDataSource.markDeleted(userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { appointmentDataSource.cancelUpcomingForClientUser(userId, any(), any()) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
                cancelled
            }
            coEvery { appointmentDataSource.anonymizeClientUser(userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { appointmentDataSource.anonymizeEmployee(userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { requestDataSource.deleteForUser(userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { appointmentPermissionDataSource.deleteForUser(userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { eventProducer.send(any<AppointmentEvent.CancelledForDeletedClient>(), any()) } coAnswers {
                sentEvents += firstArg<AppointmentEvent.CancelledForDeletedClient>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    private fun AppointmentEvent.CancelledForDeletedClient.withoutIdempotencyKey() = copy(idempotencyKey = "")

    @Test
    fun `should erase the user as client and as employee and delete their requests and permissions in one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubWrites(userId)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeClientUser(userId) }
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeEmployee(userId) }
        coVerify(exactly = 1) { fixture.requestDataSource.deleteForUser(userId) }
        coVerify(exactly = 1) { fixture.appointmentPermissionDataSource.deleteForUser(userId) }
        assertEquals(listOf(true, true, true, true, true, true), fixture.writesInTransaction)
    }

    @Test
    fun `should record the deletion first, then lock and cancel the user's upcoming appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubWrites(userId)

        whenn()
        fixture.sut(userId)

        then()
        coVerifyOrder {
            fixture.deletedUserDataSource.markDeleted(userId)
            fixture.appointmentDataSource.cancelUpcomingForClientUser(userId, any(), any())
            fixture.appointmentDataSource.anonymizeClientUser(userId)
            fixture.appointmentDataSource.anonymizeEmployee(userId)
        }
    }

    @Test
    fun `should cancel the user's upcoming appointments as client with the account deletion reason`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val startingAfter = mutableListOf<Instant>()
        fixture.stubWrites(userId)
        coEvery {
            fixture.appointmentDataSource.cancelUpcomingForClientUser(userId, capture(startingAfter), any())
        } returns emptyList()
        val before = Clock.System.now()

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.appointmentDataSource.cancelUpcomingForClientUser(userId, any(), "User deleted their account")
        }
        assertTrue(startingAfter.single() in before..(before + 5.seconds))
    }

    @Test
    fun `should tell the assigned employee about every appointment cancelled by the account deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = BusinessSnapshot.stub().copy(timeZone = TimeZone.of("Europe/Kyiv"))
        val first = Appointment.stub(businessId = business.id)
        val second = Appointment.stub(businessId = business.id)
        fixture.stubWrites(userId, cancelled = listOf(first, second))
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
                idempotencyKey = ""
            )
        }
        assertEquals(expected, fixture.sentEvents.map { it.withoutIdempotencyKey() })
        assertEquals(listOf(true, true), fixture.sentInTransaction)
    }

    @Test
    fun `should not notify the deleted user about their own appointment at a business where they also work`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = BusinessSnapshot.stub()
        val ownAppointment = Appointment.stub(businessId = business.id).let { it.copy(employee = it.employee.copy(userId = userId)) }
        val otherEmployee = Appointment.stub(businessId = business.id)
        fixture.stubWrites(userId, cancelled = listOf(ownAppointment, otherEmployee))
        coEvery { fixture.subscriptionDataSource.getBusinessSnapshot(business.id) } returns business

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(listOf(otherEmployee.employee.userId), fixture.sentEvents.map { it.employeeUserId })
    }

    @Test
    fun `should still erase the user when a cancelled appointment's business is no longer known`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val orphan = Appointment.stub()
        fixture.stubWrites(userId, cancelled = listOf(orphan))
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
        fixture.stubWrites(userId)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AppointmentEvent.CancelledForDeletedClient>(), any()) }
    }

    @Test
    fun `should fail so the event is retried when a write fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubWrites(userId)
        coEvery { fixture.appointmentDataSource.anonymizeClientUser(userId) } throws IllegalStateException("database down")

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isFailure)
    }
}
