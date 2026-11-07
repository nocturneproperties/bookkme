package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class DeleteExpiredTombstonesImplTest {

    private class SutFixture {
        val deletedUserDataSource = mockk<DeletedUserDataSource>()
        val deletedClientDataSource = mockk<DeletedClientDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteExpiredTombstonesImpl(deletedUserDataSource, deletedClientDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
        }
    }

    @Test
    fun `should forget deleted users and clients marked more than 60 days ago`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userCutoff = slot<Instant>()
        val clientCutoff = slot<Instant>()
        coEvery { fixture.deletedUserDataSource.deleteMarkedBefore(capture(userCutoff)) } returns Unit
        coEvery { fixture.deletedClientDataSource.deleteMarkedBefore(capture(clientCutoff)) } returns Unit
        val startedAt = Clock.System.now()

        whenn()
        val result = fixture.sut()

        then()
        val finishedAt = Clock.System.now()
        assertTrue(result.isSuccess)
        assertTrue(userCutoff.captured in (startedAt - 60.days)..(finishedAt - 60.days))
        assertEquals(userCutoff.captured, clientCutoff.captured)
    }

    @Test
    fun `should delete in a read committed transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deletedUserDataSource.deleteMarkedBefore(any()) } returns Unit
        coEvery { fixture.deletedClientDataSource.deleteMarkedBefore(any()) } returns Unit

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 1) { fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any()) }
    }

    @Test
    fun `should fail when the tombstones cannot be deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("database down")
        coEvery { fixture.deletedUserDataSource.deleteMarkedBefore(any()) } throws error

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
