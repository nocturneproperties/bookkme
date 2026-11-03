package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.export.DataExport
import library.export.DataExportStatus
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetBusinessDataExportImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val dataExports = mockk<DataExports>()
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBusinessDataExportImpl(dataExports, businessDataSource, transactionManager)
        val ready = DataExport.stub(status = DataExportStatus.READY)

        init {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.isOwner(userId, businessId) } returns true
            coEvery { dataExports.status(businessId) } returns ready
        }
    }

    @Test
    fun `should report the business's export to its owner`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertEquals(fixture.ready, result.getOrNull())
    }

    @Test
    fun `should not report the business's export to anyone else`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessDataSource.isOwner(fixture.userId, fixture.businessId) } returns false

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.dataExports.status(any()) }
    }
}
