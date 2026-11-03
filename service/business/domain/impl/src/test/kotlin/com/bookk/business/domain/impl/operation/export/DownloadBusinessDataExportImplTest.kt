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
import library.export.DataExportDocument
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DownloadBusinessDataExportImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val dataExports = mockk<DataExports>()
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DownloadBusinessDataExportImpl(dataExports, businessDataSource, transactionManager)

        init {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.isOwner(userId, businessId) } returns true
        }
    }

    @Test
    fun `should hand the ready document to the owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val document = DataExportDocument(byteArrayOf(1, 2, 3))
        coEvery { fixture.dataExports.document(fixture.businessId) } returns document

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertSame(document, result.getOrNull())
    }

    @Test
    fun `should report no document when none is ready`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataExports.document(fixture.businessId) } returns null

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should not hand the document to anyone but the owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessDataSource.isOwner(fixture.userId, fixture.businessId) } returns false

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.dataExports.document(any()) }
    }
}
