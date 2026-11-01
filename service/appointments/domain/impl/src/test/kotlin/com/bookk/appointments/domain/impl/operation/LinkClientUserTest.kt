package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class LinkClientUserTest {

    private class SutFixture {
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentRequestDataSource = mockk<AppointmentRequestDataSource>()
        val deleteUserAppointmentData = mockk<DeleteUserAppointmentData>()
        val transactionManager = mockk<TransactionManager>()
        val writesInTransaction = mutableListOf<Boolean>()
        val sut = LinkClientUser(
            deletedUserDataSource,
            appointmentDataSource,
            appointmentRequestDataSource,
            deleteUserAppointmentData,
            transactionManager
        )

        fun stubLink(businessId: Uuid, clientId: Uuid, userId: Uuid, userDeleted: Boolean) {
            transactionManager.mockTransaction()
            coEvery { deletedUserDataSource.isDeletedForShare(userId) } returns userDeleted
            coEvery { appointmentDataSource.reassignClient(businessId, any(), clientId) } returns Unit
            coEvery { appointmentRequestDataSource.reassignClient(businessId, any(), clientId) } returns Unit
            coEvery { appointmentDataSource.linkClientUser(businessId, clientId, userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { appointmentRequestDataSource.linkClientUser(businessId, clientId, userId) } coAnswers {
                writesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { deleteUserAppointmentData(userId) } returns Result.success(Unit)
        }
    }

    @Test
    fun `should give the client's appointments and requests the linked account in one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = false)

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = emptyList())

        then()
        assertTrue(result.isSuccess)
        assertEquals(listOf(true, true), fixture.writesInTransaction)
        coVerify(exactly = 0) { fixture.deleteUserAppointmentData(any()) }
    }

    @Test
    fun `should check whether the account was deleted before linking anything`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = false)

        whenn()
        fixture.sut(businessId, clientId, userId, mergedClientIds = emptyList())

        then()
        coVerifyOrder {
            fixture.deletedUserDataSource.isDeletedForShare(userId)
            fixture.appointmentDataSource.linkClientUser(businessId, clientId, userId)
        }
    }

    @Test
    fun `should move the merged duplicates' rows onto the kept client before linking them all`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        val first = Uuid.random()
        val second = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = false)

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = listOf(first, second))

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.deletedUserDataSource.isDeletedForShare(userId)
            fixture.appointmentDataSource.reassignClient(businessId, first, clientId)
            fixture.appointmentRequestDataSource.reassignClient(businessId, first, clientId)
            fixture.appointmentDataSource.reassignClient(businessId, second, clientId)
            fixture.appointmentRequestDataSource.reassignClient(businessId, second, clientId)
            fixture.appointmentDataSource.linkClientUser(businessId, clientId, userId)
            fixture.appointmentRequestDataSource.linkClientUser(businessId, clientId, userId)
        }
    }

    @Test
    fun `should erase the newly linked rows when the account was already deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = true)

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = emptyList())

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.appointmentDataSource.linkClientUser(businessId, clientId, userId)
            fixture.appointmentRequestDataSource.linkClientUser(businessId, clientId, userId)
            fixture.deleteUserAppointmentData(userId)
        }
    }

    @Test
    fun `should fail so the event is retried when erasing the linked rows fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = true)
        coEvery { fixture.deleteUserAppointmentData(userId) } returns Result.failure(IllegalStateException("database down"))

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = emptyList())

        then()
        assertTrue(result.isFailure)
    }
}
