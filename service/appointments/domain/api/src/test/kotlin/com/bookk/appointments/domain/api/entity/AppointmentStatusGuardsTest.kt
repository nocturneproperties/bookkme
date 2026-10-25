package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class AppointmentStatusGuardsTest {

    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val started = now - 1.minutes
    private val notStarted = now + 1.minutes

    private fun appointment(status: AppointmentStatus, date: Instant = started) =
        Appointment.stub(date = date).copy(status = status)

    @Test
    fun `should allow a scheduled appointment to be changed`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)

        whenn()
        val error = runCatching { appointment.requireScheduled() }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject changing a cancelled appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.CANCELLED)

        whenn()
        val error = runCatching { appointment.requireScheduled() }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.AlreadyCancelled)
    }

    @Test
    fun `should reject changing a completed appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)

        whenn()
        val error = runCatching { appointment.requireScheduled() }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.AlreadyCompleted)
    }

    @Test
    fun `should reject changing a no-show appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.NO_SHOW)

        whenn()
        val error = runCatching { appointment.requireScheduled() }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.MarkedNoShow)
    }

    @Test
    fun `should allow completing a started scheduled appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should treat an appointment starting exactly now as started`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED, date = now)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should allow completing an already completed appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject completing an appointment that has not started`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED, date = notStarted)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.NotStarted)
    }

    @Test
    fun `should reject completing a cancelled appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.CANCELLED)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.AlreadyCancelled)
    }

    @Test
    fun `should reject completing a no-show appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.NO_SHOW)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.MarkedNoShow)
    }

    @Test
    fun `should allow marking a started scheduled appointment as no-show`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should allow marking a completed appointment as no-show`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should allow marking an already no-show appointment as no-show`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.NO_SHOW)

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject marking a cancelled appointment as no-show`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.CANCELLED)

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.AlreadyCancelled)
    }

    @Test
    fun `should reject marking a scheduled appointment that has not started as no-show`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED, date = notStarted)

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.NotStarted)
    }

    @Test
    fun `should reject marking a completed appointment that has not started as no-show`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED, date = notStarted)

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.NotStarted)
    }
}
