package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.GetUpcomingBookings
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetUpcomingBookingsImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetUpcomingBookingsImpl(appointmentDataSource, requestDataSource, transactionManager)
    }

    @Test
    fun `should return the caller's nearest appointments and pending requests`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointments = listOf(Appointment.stub(note = ""), Appointment.stub(note = ""))
        val requests = listOf(AppointmentRequest.stub(initiatorUserId = userId))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                appointmentDataSource.getUpcomingOfClientUser(userId, any(), GetUpcomingBookings.LIMIT)
            } returns appointments
            coEvery {
                requestDataSource.getUpcomingPendingOfUser(userId, any(), GetUpcomingBookings.LIMIT)
            } returns requests
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertEquals(appointments, result.getOrThrow().appointments)
        assertEquals(requests, result.getOrThrow().requests)
    }

    @Test
    fun `should hide the business's note and keep the client's note on appointments`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub(note = "Allergic to latex", clientNote = "Please call on arrival")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getUpcomingOfClientUser(userId, any(), any()) } returns listOf(appointment)
            coEvery { requestDataSource.getUpcomingPendingOfUser(userId, any(), any()) } returns emptyList()
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        val returned = result.getOrThrow().appointments.single()
        assertEquals("", returned.note)
        assertEquals("Please call on arrival", returned.clientNote)
    }

    @Test
    fun `should look for appointments that have not ended and requests that have not started`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointmentsEndingAfter = mutableListOf<Instant>()
        val requestsStartingAfter = mutableListOf<Instant>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery {
                appointmentDataSource.getUpcomingOfClientUser(userId, capture(appointmentsEndingAfter), any())
            } returns emptyList()
            coEvery {
                requestDataSource.getUpcomingPendingOfUser(userId, capture(requestsStartingAfter), any())
            } returns emptyList()
        }
        val before = Clock.System.now()

        whenn()
        fixture.sut(userId)

        then()
        assertTrue(appointmentsEndingAfter.single() in before..(before + 5.seconds))
        assertEquals(appointmentsEndingAfter.single(), requestsStartingAfter.single())
    }

    @Test
    fun `should return empty lists for a caller without bookings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getUpcomingOfClientUser(userId, any(), any()) } returns emptyList()
            coEvery { requestDataSource.getUpcomingPendingOfUser(userId, any(), any()) } returns emptyList()
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.getOrThrow().appointments.isEmpty())
        assertTrue(result.getOrThrow().requests.isEmpty())
    }
}
