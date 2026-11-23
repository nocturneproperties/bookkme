package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.BookingConflictError
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class EmployeeBookingsTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val sut = EmployeeBookings(appointmentDataSource)
        val start = Instant.parse("2030-06-03T10:00:00Z")
        val booking = AppointmentRequest.stub(date = start)
        val settings = AppointmentSettings.stub(booking.businessId).copy(inBetweenBreakInMinutes = 10)
        val searched = start - 10.minutes..<booking.dateEnd + 10.minutes

        fun givenBookings(appointments: List<BookedRange>) {
            coEvery {
                appointmentDataSource.getEmployeeBookings(booking.businessId, booking.employee.id, searched)
            } returns appointments
        }
    }

    @Test
    fun `should accept a booking when the employee has nothing else within the break around it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBookings(emptyList())

        whenn()
        val error = runCatching { fixture.sut.requireFree(fixture.booking, fixture.settings) }.exceptionOrNull()

        then()
        assertNull(error)
        coVerify(exactly = 1) {
            fixture.appointmentDataSource.getEmployeeBookings(
                fixture.booking.businessId,
                fixture.booking.employee.id,
                fixture.searched,
            )
        }
    }

    @Test
    fun `should reject a booking overlapping an appointment of the employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBookings(listOf(BookedRange.stub(start = fixture.start)))

        whenn()
        val error = runCatching { fixture.sut.requireFree(fixture.booking, fixture.settings) }.exceptionOrNull()

        then()
        assertTrue(error is BookingConflictError.EmployeeBusy)
    }

    @Test
    fun `should not count the booking itself as a conflict`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.givenBookings(listOf(BookedRange.stub(id = fixture.booking.id, start = fixture.start)))

        whenn()
        val error = runCatching { fixture.sut.requireFree(fixture.booking, fixture.settings) }.exceptionOrNull()

        then()
        assertNull(error)
    }
}
