package com.bookk.business.domain.impl.operation.encryption

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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EncryptPlaintextContactsImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = EncryptPlaintextContactsImpl(clientDataSource, employeeDataSource, transactionManager)
        val batchesInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
        }

        fun encryptClientBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { clientDataSource.encryptPlaintextClients(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }

        fun encryptEmployeeBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { employeeDataSource.encryptPlaintextEmployees(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }
    }

    @Test
    fun `should encrypt client batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptClientBatches(BATCH, BATCH, 3)
        fixture.encryptEmployeeBatches(0)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 3) { fixture.clientDataSource.encryptPlaintextClients(BATCH) }
    }

    @Test
    fun `should encrypt employee batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptClientBatches(0)
        fixture.encryptEmployeeBatches(BATCH, 7)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 2) { fixture.employeeDataSource.encryptPlaintextEmployees(BATCH) }
    }

    @Test
    fun `should encrypt every batch in a transaction of its own`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptClientBatches(BATCH, 0)
        fixture.encryptEmployeeBatches(0)

        whenn()
        fixture.sut.invoke()

        then()
        assertEquals(listOf(true, true, true), fixture.batchesInTransaction)
    }

    @Test
    fun `should stop after one query per table when no plaintext contact is left`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptClientBatches(0)
        fixture.encryptEmployeeBatches(0)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.clientDataSource.encryptPlaintextClients(BATCH) }
        coVerify(exactly = 1) { fixture.employeeDataSource.encryptPlaintextEmployees(BATCH) }
    }

    @Test
    fun `should fail without touching employees when a client batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("The stored client has a shredded key")
        coEvery { fixture.clientDataSource.encryptPlaintextClients(any()) } throws error

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.employeeDataSource.encryptPlaintextEmployees(any()) }
    }

    private companion object {
        const val BATCH = 100
    }
}
