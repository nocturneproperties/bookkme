package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class DeleteExpiredRequestsImplTest {

    private class SutFixture {
        val requestDataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteExpiredRequestsImpl(requestDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
        }
    }

    @Test
    fun `should delete requests whose slot ended more than 60 days ago`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = slot<Instant>()
        coEvery { fixture.requestDataSource.deleteEndedBefore(capture(cutoff)) } returns Unit
        val startedAt = Clock.System.now()

        whenn()
        val result = fixture.sut()

        then()
        val finishedAt = Clock.System.now()
        assertTrue(result.isSuccess)
        assertTrue(cutoff.captured in (startedAt - 60.days)..(finishedAt - 60.days))
    }

    @Test
    fun `should delete in a read committed transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.requestDataSource.deleteEndedBefore(any()) } returns Unit

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 1) {
            fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any())
        }
    }

    @Test
    fun `should fail when the requests cannot be deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("database down")
        coEvery { fixture.requestDataSource.deleteEndedBefore(any()) } throws error

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
