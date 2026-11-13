package com.bookk.business.domain.impl.operation.user

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
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteUserBusinessDataImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val deletesInTransaction = mutableListOf<Boolean>()
        val sut = DeleteUserBusinessDataImpl(transactionManager, clientDataSource, employeeDataSource)

        fun stubDeletes(userId: Uuid) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.deleteClientsByUserId(userId) } coAnswers {
                deletesInTransaction += transactionManager.isInTransaction()
                emptyList()
            }
            coEvery { employeeDataSource.deleteEmployeesByUserId(userId) } coAnswers {
                deletesInTransaction += transactionManager.isInTransaction()
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
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClientsByUserId(userId) }
        coVerify(exactly = 1) { fixture.employeeDataSource.deleteEmployeesByUserId(userId) }
        assertEquals(listOf(true, true), fixture.deletesInTransaction)
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
}
