package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.export.DataExportDocument
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DownloadPersonalDataExportImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val dataExports = mockk<DataExports>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DownloadPersonalDataExportImpl(dataExports, transactionManager)

        init {
            transactionManager.mockTransaction()
        }
    }

    @Test
    fun `should hand out the caller's ready document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val document = DataExportDocument(byteArrayOf(1, 2, 3))
        coEvery { fixture.dataExports.document(fixture.userId) } returns document

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertSame(document, result.getOrNull())
    }

    @Test
    fun `should report no document when none is ready`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataExports.document(fixture.userId) } returns null

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }
}
