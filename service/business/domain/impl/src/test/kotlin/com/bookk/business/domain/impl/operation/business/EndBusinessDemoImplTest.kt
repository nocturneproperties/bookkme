package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EndBusinessDemoImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = EndBusinessDemoImpl(businessDataSource, transactionManager)
        val endedInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
        }
    }

    @Test
    fun `should clear the demo flag of the owner's businesses in a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ownerId = Uuid.random()
        coEvery { fixture.businessDataSource.endDemoOfBusinessesOwnedBy(ownerId) } coAnswers {
            fixture.endedInTransaction += fixture.transactionManager.isInTransaction()
        }

        whenn()
        val result = fixture.sut(ownerId)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(listOf(true), fixture.endedInTransaction)
    }
}
