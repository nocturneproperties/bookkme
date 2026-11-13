package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteBusinessImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val dataExports = mockk<DataExports>()
        val discardedInTransaction = mutableListOf<Pair<List<Uuid>, Boolean>>()
        val sut = DeleteBusinessImpl(businessDataSource, transactionManager, eventProducer, dataExports)

        init {
            coEvery { dataExports.discardAll(any()) } coAnswers {
                discardedInTransaction += firstArg<Collection<Uuid>>().toList() to transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should delete business successfully and send events when user has businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val deletedIds = listOf(Uuid.random(), Uuid.random())
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.deleteUserBusinesses(userId) } returns deletedIds
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { fixture.eventProducer.send(any(), any()) }
    }

    @Test
    fun `should delete the data exports of all deleted businesses at once in the same transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val deletedIds = listOf(Uuid.random(), Uuid.random())
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.businessDataSource.deleteUserBusinesses(userId) } returns deletedIds

        whenn()
        fixture.sut(userId)

        then()
        assertEquals(listOf(deletedIds to true), fixture.discardedInTransaction)
    }
}
