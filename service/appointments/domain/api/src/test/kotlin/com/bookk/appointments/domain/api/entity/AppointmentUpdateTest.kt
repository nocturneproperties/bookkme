package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentUpdateTest {

    private val appointment = Appointment.stub(date = Instant.parse("2099-01-01T10:00:00Z"))

    private fun keeping(appointment: Appointment) = AppointmentUpdate.stub(
        id = appointment.id,
        date = appointment.date,
        employeeId = appointment.employee.id,
    )

    @Test
    fun `should reschedule when the date changes`() = runUnitTest {
        given()
        val update = keeping(appointment).copy(date = appointment.date + 1.hours)

        whenn()
        val reschedules = update.reschedules(appointment)

        then()
        assertTrue(reschedules)
    }

    @Test
    fun `should reschedule when the employee changes`() = runUnitTest {
        given()
        val update = keeping(appointment).copy(employeeId = Uuid.random())

        whenn()
        val reschedules = update.reschedules(appointment)

        then()
        assertTrue(reschedules)
    }

    @Test
    fun `should not reschedule when only the note and services change`() = runUnitTest {
        given()
        val update = keeping(
            appointment,
        ).copy(note = "Corrected note", services = listOf(RequestedService.stub(count = 3)))

        whenn()
        val reschedules = update.reschedules(appointment)

        then()
        assertFalse(reschedules)
    }
}
