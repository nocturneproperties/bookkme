package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class IndexClientNamesImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = IndexClientNamesImpl(appointmentDataSource, transactionManager)
        val batchesInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
        }

        fun indexBatchesEndingAt(vararg cursors: Uuid?) {
            val remaining = cursors.toMutableList()
            coEvery { appointmentDataSource.indexClientNames(any(), any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }
    }

    @Test
    fun `should index batches after the previous batch's last appointment until none is left`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = Uuid.random()
        val second = Uuid.random()
        fixture.indexBatchesEndingAt(first, second, null)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerifyOrder {
            fixture.appointmentDataSource.indexClientNames(Uuid.NIL, BATCH)
            fixture.appointmentDataSource.indexClientNames(first, BATCH)
            fixture.appointmentDataSource.indexClientNames(second, BATCH)
        }
    }

    @Test
    fun `should not walk the appointments again once a pass has reached their end`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.indexBatchesEndingAt(Uuid.random(), null)
        fixture.sut.invoke()

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 2) { fixture.appointmentDataSource.indexClientNames(any(), any()) }
    }

    @Test
    fun `should walk the appointments again from the start after a pass failed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.appointmentDataSource.indexClientNames(any(), any()) } throws IllegalStateException("down")
        fixture.sut.invoke()
        fixture.indexBatchesEndingAt(null)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 2) { fixture.appointmentDataSource.indexClientNames(Uuid.NIL, BATCH) }
    }

    @Test
    fun `should index every batch in a read committed transaction of its own`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.indexBatchesEndingAt(Uuid.random(), null)

        whenn()
        fixture.sut.invoke()

        then()
        assertEquals(listOf(true, true), fixture.batchesInTransaction)
        coVerify(exactly = 2) {
            fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any())
        }
    }

    @Test
    fun `should fail when a batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("batch failed")
        coEvery { fixture.appointmentDataSource.indexClientNames(any(), any()) } throws error

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertSame(error, result.exceptionOrNull())
    }

    private companion object {
        const val BATCH = 100
    }
}
