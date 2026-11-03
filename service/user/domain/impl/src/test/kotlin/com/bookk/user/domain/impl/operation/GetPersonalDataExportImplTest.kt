package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.export.DataExport
import library.export.DataExportStatus
import library.export.DataExports
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetPersonalDataExportImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val dataExports = mockk<DataExports>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetPersonalDataExportImpl(dataExports, transactionManager)
        val readInTransaction = mutableListOf<Boolean>()
        val ready = DataExport.stub(status = DataExportStatus.READY)

        init {
            transactionManager.mockTransaction()
            coEvery { dataExports.status(userId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                ready
            }
        }
    }

    @Test
    fun `should report the caller's export inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(fixture.ready, result.getOrNull())
        assertEquals(listOf(true), fixture.readInTransaction)
    }
}
