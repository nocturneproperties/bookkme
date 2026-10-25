package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AppointmentRequestStatusGuardsTest {

    private fun request(status: AppointmentRequestStatus) = AppointmentRequest.stub().copy(status = status)

    @Test
    fun `should allow acting on a pending request`() = runUnitTest {
        given()
        val request = request(AppointmentRequestStatus.PENDING)

        whenn()
        val error = runCatching { request.requirePending() }.exceptionOrNull()

        then()
        assertNull(error)
    }

    @Test
    fun `should reject acting on an approved request`() = runUnitTest {
        given()
        val request = request(AppointmentRequestStatus.APPROVED)

        whenn()
        val error = runCatching { request.requirePending() }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentRequestStatusError.AlreadyApproved)
    }

    @Test
    fun `should reject acting on a declined request`() = runUnitTest {
        given()
        val request = request(AppointmentRequestStatus.DECLINED)

        whenn()
        val error = runCatching { request.requirePending() }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentRequestStatusError.AlreadyDeclined)
    }

    @Test
    fun `should reject acting on a cancelled request as already declined`() = runUnitTest {
        given()
        val request = request(AppointmentRequestStatus.CANCELLED)

        whenn()
        val error = runCatching { request.requirePending() }.exceptionOrNull()

        then()
        assertTrue(error is AppointmentRequestStatusError.AlreadyDeclined)
    }
}
