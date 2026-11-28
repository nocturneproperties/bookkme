package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.PendingRequestRef
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DeleteOutdatedRequestsImplTest {

    private class SutFixture {
        val appointmentRequestDataSource = mockk<AppointmentRequestDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val sentEvents = mutableListOf<AppointmentEvent>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut = DeleteOutdatedRequestsImpl(appointmentRequestDataSource, eventProducer, transactionManager)

        init {
            coEvery { eventProducer.send(any<AppointmentEvent>(), any()) } coAnswers {
                sentEvents += firstArg<AppointmentEvent>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        fun resolved(): List<PendingRequestRef> {
            return sentEvents.filterIsInstance<AppointmentEvent.RequestResolved>()
                .map { PendingRequestRef(it.requestId, it.employeeUserId) }
        }
    }

    @Test
    fun `should cancel outdated pending requests`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } returns emptyList()
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentRequestDataSource.cancelOutdated(any()) }
    }

    @Test
    fun `should cancel in a read committed transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } returns emptyList()
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke()

        then()
        coVerify(exactly = 1) {
            fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any())
        }
    }

    @Test
    fun `should tell each cancelled request's employee in the transaction that it is resolved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cancelled = listOf(PendingRequestRef.stub(), PendingRequestRef.stub())
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } returns cancelled
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        assertEquals(cancelled, fixture.resolved())
        assertEquals(listOf(true, true), fixture.sentInTransaction)
    }

    @Test
    fun `should send nothing when no request was outdated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } returns emptyList()
            transactionManager.mockTransaction()
        }

        whenn()
        fixture.sut.invoke()

        then()
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should return failure when datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } throws RuntimeException("DB error")
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertTrue(fixture.sentEvents.isEmpty())
    }
}
