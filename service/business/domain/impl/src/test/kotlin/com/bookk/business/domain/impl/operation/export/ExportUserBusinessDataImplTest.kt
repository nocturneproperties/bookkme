package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.business.domain.datasource.UserDataExportDataSource
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

internal class ExportUserBusinessDataImplTest {

    private class SutFixture {
        val dataSource = mockk<UserDataExportDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = ExportUserBusinessDataImpl(dataSource, transactionManager)
        val exportedInTransaction = mutableListOf<Boolean>()
        val userId = Uuid.random()
        val export = BusinessUserDataExport.stub(clientRecords = listOf(BusinessUserDataExport.ClientRecordExport.stub()))

        init {
            transactionManager.mockTransaction()
            coEvery { dataSource.export(userId) } coAnswers {
                exportedInTransaction += transactionManager.isInTransaction()
                export
            }
        }
    }

    @Test
    fun `should export the user's business data inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(fixture.export, result.getOrNull())
        assertEquals(listOf(true), fixture.exportedInTransaction)
    }

    @Test
    fun `should fail when the data can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.export(fixture.userId) } throws IllegalStateException("database unreachable")

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.isFailure)
    }
}
