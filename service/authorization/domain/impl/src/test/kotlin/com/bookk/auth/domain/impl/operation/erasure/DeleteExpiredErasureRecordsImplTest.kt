package com.bookk.auth.domain.impl.operation.erasure

import com.bookk.auth.domain.datasource.ErasedAccountDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class DeleteExpiredErasureRecordsImplTest {

    private class SutFixture {
        val erasedAccountDataSource = mockk<ErasedAccountDataSource>()
        val transactionManager = mockk<TransactionManager>().apply { mockTransaction() }
        val sut = DeleteExpiredErasureRecordsImpl(erasedAccountDataSource, transactionManager)
    }

    @Test
    fun `should delete the records of erasures older than 60 days`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = slot<Instant>()
        val deletedInTransaction = mutableListOf<Boolean>()
        coEvery { fixture.erasedAccountDataSource.deleteRecordedBefore(capture(cutoff)) } coAnswers {
            deletedInTransaction += fixture.transactionManager.isInTransaction()
        }

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertTrue((Clock.System.now() - 60.days - cutoff.captured).absoluteValue < 1.minutes)
        assertEquals(listOf(true), deletedInTransaction)
    }

    @Test
    fun `should report a failed cleanup`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("cleanup failed")
        coEvery { fixture.erasedAccountDataSource.deleteRecordedBefore(any()) } throws error

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
