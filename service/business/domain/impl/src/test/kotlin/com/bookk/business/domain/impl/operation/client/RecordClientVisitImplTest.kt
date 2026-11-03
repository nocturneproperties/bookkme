package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class RecordClientVisitImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val recordedInTransaction = mutableListOf<Boolean>()
        val sut = RecordClientVisitImpl(clientDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
        }
    }

    @Test
    fun `should record the visit on the business's client inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val clientId = Uuid.random()
        val visitEnd = Instant.fromEpochMilliseconds(1_000_000)
        coEvery { fixture.clientDataSource.recordVisit(businessId, clientId, visitEnd) } coAnswers {
            fixture.recordedInTransaction += fixture.transactionManager.isInTransaction()
        }

        whenn()
        val result = fixture.sut(businessId, clientId, visitEnd)

        then()
        assertTrue(result.isSuccess)
        assertEquals(listOf(true), fixture.recordedInTransaction)
    }

    @Test
    fun `should fail when the visit cannot be recorded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("database down")
        coEvery { fixture.clientDataSource.recordVisit(any(), any(), any()) } throws error

        whenn()
        val result = fixture.sut(Uuid.random(), Uuid.random(), Instant.fromEpochMilliseconds(1_000_000))

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
