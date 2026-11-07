package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GetLegalDocumentsImplTest {

    private class SutFixture {
        val legalDocumentDataSource = mockk<LegalDocumentDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetLegalDocumentsImpl(legalDocumentDataSource, transactionManager)
    }

    @Test
    fun `should return the versions in effect`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val versions = listOf(
            LegalDocumentVersion.stub(),
            LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE),
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns versions
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertEquals(versions, result.getOrNull())
    }

    @Test
    fun `should return failure when the datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } throws RuntimeException("database down")
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
    }
}
