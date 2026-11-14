package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
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
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteUserBusinessDataImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val dataExports = mockk<DataExports>()
        val deletesInTransaction = mutableListOf<Boolean>()
        val discardedInTransaction = mutableListOf<Pair<Set<Uuid>, Boolean>>()
        val subjectKeys = ShredRecordingSubjectKeys(transactionManager::isInTransaction)
        val sut = DeleteUserBusinessDataImpl(
            transactionManager,
            clientDataSource,
            employeeDataSource,
            dataExports,
            subjectKeys,
        )

        fun stubDeletes(
            userId: Uuid,
            erasedClients: List<ErasedClient> = emptyList(),
            employeeBusinessIds: List<Uuid> = emptyList(),
        ) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientsOfUserForUpdate(userId) } returns erasedClients
            coEvery { employeeDataSource.getBusinessIdsOfUserForUpdate(userId) } returns employeeBusinessIds
            coEvery { clientDataSource.deleteClients(erasedClients.map { it.clientId }) } coAnswers {
                deletesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { employeeDataSource.deleteEmployeesByUserId(userId) } coAnswers {
                deletesInTransaction += transactionManager.isInTransaction()
            }
            coEvery { dataExports.discardAll(any()) } coAnswers {
                discardedInTransaction += firstArg<Collection<Uuid>>().toSet() to transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should delete the user's client and employee rows in one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubDeletes(userId)

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClients(emptyList()) }
        coVerify(exactly = 1) { fixture.employeeDataSource.deleteEmployeesByUserId(userId) }
        assertEquals(listOf(true, true), fixture.deletesInTransaction)
    }

    @Test
    fun `should select the user's client and employee rows before deleting them`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val erasedClients = listOf(ErasedClient.stub())
        fixture.stubDeletes(userId, erasedClients = erasedClients)

        whenn()
        fixture.sut(userId)

        then()
        coVerifyOrder {
            fixture.clientDataSource.getClientsOfUserForUpdate(userId)
            fixture.employeeDataSource.getBusinessIdsOfUserForUpdate(userId)
            fixture.clientDataSource.deleteClients(erasedClients.map { it.clientId })
            fixture.employeeDataSource.deleteEmployeesByUserId(userId)
        }
    }

    @Test
    fun `should discard the data exports of every business that held the user's data at once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val clientBusiness = Uuid.random()
        val employeeBusiness = Uuid.random()
        val bothBusiness = Uuid.random()
        fixture.stubDeletes(
            userId,
            erasedClients = listOf(ErasedClient.stub(businessId = clientBusiness), ErasedClient.stub(bothBusiness)),
            employeeBusinessIds = listOf(employeeBusiness, bothBusiness),
        )

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        val expected = listOf(setOf(clientBusiness, bothBusiness, employeeBusiness) to true)
        assertEquals(expected, fixture.discardedInTransaction)
    }

    @Test
    fun `should discard no data export when the user had no client or employee rows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubDeletes(userId)

        whenn()
        fixture.sut(userId)

        then()
        coVerify(exactly = 0) { fixture.dataExports.discardAll(any()) }
    }

    @Test
    fun `should fail so the event is retried when a delete fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.stubDeletes(userId)
        coEvery { fixture.employeeDataSource.deleteEmployeesByUserId(userId) } throws
            IllegalStateException("database down")

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should shred the user's key and the keys of their client rows in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val erasedClients = listOf(ErasedClient.stub(), ErasedClient.stub())
        fixture.stubDeletes(userId, erasedClients = erasedClients)

        whenn()
        fixture.sut(userId)

        then()
        assertEquals(setOf(userId) + erasedClients.map { it.clientId }, fixture.subjectKeys.shreddedInTransaction)
    }
}
