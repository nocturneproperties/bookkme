package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class AppointmentStatusGuardsTest {

    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val started = now - 1.minutes
    private val notStarted = now + 1.minutes

    private fun appointment(status: AppointmentStatus, date: Instant = started) = Appointment.stub(date = date).copy(status = status)

    @Test
    fun `should allow a scheduled appointment to be changed`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)

        whenn()
        val error = runCatching { appointment.requireEditable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject changing a cancelled appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.CANCELLED)

        whenn()
        val error = runCatching { appointment.requireEditable(now) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.AlreadyCancelled)
    }

    @Test
    fun `should allow changing a completed appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)

        whenn()
        val error = runCatching { appointment.requireEditable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should allow changing a no-show appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.NO_SHOW)

        whenn()
        val error = runCatching { appointment.requireEditable(now) }.exceptionOrNull()

        then()
        assertNull(error)
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
    fun `should allow completing a no-show appointment`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.NO_SHOW)

        whenn()
        val error = runCatching { appointment.requireCompletable(now) }.exceptionOrNull()

        then()
        assertNull(error)
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

    @Test
    fun `should reject changing a scheduled appointment that ended more than a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)
        val laterThanTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes

        whenn()
        val error = runCatching { appointment.requireEditable(laterThanTheWindow) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.EditWindowClosed)
    }

    @Test
    fun `should allow changing a scheduled appointment that ended exactly a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)
        val endOfTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW

        whenn()
        val error = runCatching { appointment.requireEditable(endOfTheWindow) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject completing a scheduled appointment that ended more than a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)
        val laterThanTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes

        whenn()
        val error = runCatching { appointment.requireCompletable(laterThanTheWindow) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.EditWindowClosed)
    }

    @Test
    fun `should allow completing a scheduled appointment that ended exactly a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)
        val endOfTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW

        whenn()
        val error = runCatching { appointment.requireCompletable(endOfTheWindow) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject adjusting a completed appointment that ended more than a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)
        val laterThanTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes

        whenn()
        val error = runCatching { appointment.requireCompletable(laterThanTheWindow) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.EditWindowClosed)
    }

    @Test
    fun `should allow adjusting a completed appointment that ended exactly a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)
        val endOfTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW

        whenn()
        val error = runCatching { appointment.requireCompletable(endOfTheWindow) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject marking a scheduled appointment as no-show that ended more than a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)
        val laterThanTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(laterThanTheWindow) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.EditWindowClosed)
    }

    @Test
    fun `should allow marking a scheduled appointment as no-show that ended exactly a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.SCHEDULED)
        val endOfTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(endOfTheWindow) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject marking a completed appointment as no-show that ended more than a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)
        val laterThanTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(laterThanTheWindow) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.EditWindowClosed)
    }

    @Test
    fun `should allow marking a completed appointment as no-show that ended exactly a week ago`() = runUnitTest {
        given()
        val appointment = appointment(AppointmentStatus.COMPLETED)
        val endOfTheWindow = appointment.dateEnd + Appointment.EDIT_WINDOW

        whenn()
        val error = runCatching { appointment.requireNoShowMarkable(endOfTheWindow) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should keep the edit window to one week`() = runUnitTest {
        given()
        val expected = 7.days

        whenn()
        val window = Appointment.EDIT_WINDOW

        then()
        assertTrue(window == expected)
    }

    @Test
    fun `should allow correcting a system completed appointment within its edit window`() = runUnitTest {
        given()
        val appointment = Appointment.stub(date = started).copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.SYSTEM,
        )

        whenn()
        val error = runCatching { appointment.requireEditable(now) }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject all corrections of an unresolved appointment`() = runUnitTest {
        given()
        val appointment = Appointment.stub(date = started).copy(status = AppointmentStatus.UNRESOLVED)

        whenn()
        val errors = listOf(
            runCatching { appointment.requireEditable(now) }.exceptionOrNull(),
            runCatching { appointment.requireCompletable(now) }.exceptionOrNull(),
            runCatching { appointment.requireNoShowMarkable(now) }.exceptionOrNull(),
        )

        then()
        assertTrue(errors.all { it is AppointmentStatusError.EditWindowClosed })
    }

    @Test
    fun `should reject correcting a system completed appointment after its edit window`() = runUnitTest {
        given()
        val appointment = Appointment.stub(date = started).copy(
            status = AppointmentStatus.COMPLETED,
            completedBy = AppointmentCompletedBy.SYSTEM,
        )

        whenn()
        val error = runCatching { appointment.requireEditable(appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes) }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentStatusError.EditWindowClosed)
    }

    @Test
    fun `should allow correcting every non-terminal outcome at the exact edit window boundary`() = runUnitTest {
        given()
        val appointments = listOf(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
            .map { Appointment.stub(date = started).copy(status = it) }

        whenn()
        val errors = appointments.flatMap { appointment ->
            val boundary = appointment.dateEnd + Appointment.EDIT_WINDOW
            listOf(
                runCatching { appointment.requireEditable(boundary) }.exceptionOrNull(),
                runCatching { appointment.requireCompletable(boundary) }.exceptionOrNull(),
                runCatching { appointment.requireNoShowMarkable(boundary) }.exceptionOrNull(),
            )
        }

        then()
        assertTrue(errors.all { it == null })
    }

    @Test
    fun `should reject correcting every non-terminal outcome after its edit window`() = runUnitTest {
        given()
        val appointments = listOf(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
            .map { Appointment.stub(date = started).copy(status = it) }

        whenn()
        val errors = appointments.flatMap { appointment ->
            val expired = appointment.dateEnd + Appointment.EDIT_WINDOW + 1.minutes
            listOf(
                runCatching { appointment.requireEditable(expired) }.exceptionOrNull(),
                runCatching { appointment.requireCompletable(expired) }.exceptionOrNull(),
                runCatching { appointment.requireNoShowMarkable(expired) }.exceptionOrNull(),
            )
        }

        then()
        assertTrue(errors.all { it is AppointmentStatusError.EditWindowClosed })
    }
}
