package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteClientAppointmentData
import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
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
import library.encryption.test.ShredRecordingSubjectKeys
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
        val deletedClientDataSource = mockk<DeletedClientDataSource>()
        val deleteClientAppointmentData = mockk<DeleteClientAppointmentData>()
        val transactionManager = mockk<TransactionManager>()
        val writesInTransaction = mutableListOf<Boolean>()
        val subjectKeys = ShredRecordingSubjectKeys(transactionManager::isInTransaction)
        val sut = LinkClientUser(
            deletedUserDataSource,
            appointmentDataSource,
            appointmentRequestDataSource,
            deleteUserAppointmentData,
            deletedClientDataSource,
            deleteClientAppointmentData,
            subjectKeys,
            transactionManager,
        )

        fun stubLink(
            businessId: Uuid,
            clientId: Uuid,
            userId: Uuid,
            userDeleted: Boolean,
            clientDeleted: Boolean = false,
        ) {
            transactionManager.mockTransaction()
            coEvery { deletedUserDataSource.isDeletedForShare(userId) } returns userDeleted
            coEvery { deletedClientDataSource.isDeletedForShare(businessId, clientId) } returns clientDeleted
            coEvery { deleteClientAppointmentData(businessId, clientId) } returns Result.success(Unit)
            coEvery { appointmentDataSource.reassignClients(businessId, any(), clientId) } returns Unit
            coEvery { appointmentRequestDataSource.reassignClients(businessId, any(), clientId) } returns Unit
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
            fixture.appointmentDataSource.reassignClients(businessId, listOf(first, second), clientId)
            fixture.appointmentRequestDataSource.reassignClients(businessId, listOf(first, second), clientId)
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
        coEvery { fixture.deleteUserAppointmentData(userId) } returns
            Result.failure(IllegalStateException("database down"))

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = emptyList())

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should check whether the client was deleted before linking anything`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = false)

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = listOf(Uuid.random()))

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.deletedUserDataSource.isDeletedForShare(userId)
            fixture.deletedClientDataSource.isDeletedForShare(businessId, clientId)
            fixture.appointmentDataSource.reassignClients(businessId, any(), clientId)
            fixture.appointmentDataSource.linkClientUser(businessId, clientId, userId)
        }
        coVerify(exactly = 0) { fixture.deleteClientAppointmentData(any(), any()) }
    }

    @Test
    fun `should erase the moved and linked rows when the business already deleted the client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        val merged = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = false, clientDeleted = true)

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = listOf(merged))

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.appointmentDataSource.reassignClients(businessId, listOf(merged), clientId)
            fixture.appointmentDataSource.linkClientUser(businessId, clientId, userId)
            fixture.appointmentRequestDataSource.linkClientUser(businessId, clientId, userId)
            fixture.deleteClientAppointmentData(businessId, clientId)
        }
        coVerify(exactly = 0) { fixture.deleteUserAppointmentData(any()) }
    }

    @Test
    fun `should fail so the event is retried when erasing a deleted client's rows fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        fixture.stubLink(businessId, clientId, userId, userDeleted = false, clientDeleted = true)
        coEvery { fixture.deleteClientAppointmentData(businessId, clientId) } returns
            Result.failure(IllegalStateException("database down"))

        whenn()
        val result = fixture.sut(businessId, clientId, userId, mergedClientIds = emptyList())

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should shred the keys of the clients merged into the linked client in the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val userId = Uuid.random()
        val mergedClientIds = listOf(Uuid.random(), Uuid.random())
        fixture.stubLink(businessId, clientId, userId, userDeleted = false)

        whenn()
        fixture.sut(businessId, clientId, userId, mergedClientIds)

        then()
        assertEquals(mergedClientIds.toSet(), fixture.subjectKeys.shreddedInTransaction)
    }
}
