package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteUserAppointmentDataImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteUserAppointmentDataImpl(appointmentDataSource, requestDataSource, appointmentPermissionDataSource, transactionManager)
    }

    @Test
    fun `should anonymize appointments and requests and delete permissions for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.anonymizeForUser(userId) } returns Unit
            coEvery { requestDataSource.deleteForUser(userId) } returns Unit
            coEvery { appointmentPermissionDataSource.deleteForUser(userId) } returns Unit
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.anonymizeForUser(userId) }
        coVerify(exactly = 1) { fixture.requestDataSource.deleteForUser(userId) }
        coVerify(exactly = 1) { fixture.appointmentPermissionDataSource.deleteForUser(userId) }
    }
}
