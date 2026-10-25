package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DeleteDayOffsInThePastImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteDayOffsInThePastImpl(businessDataSource, transactionManager)
    }

    @Test
    fun `should delete day offs in the past`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { businessDataSource.deleteDayOffsInThePast() } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessDataSource.deleteDayOffsInThePast() }
    }
}
