package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentStatus
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetLegalDocumentStatusImplTest {

    private class SutFixture {
        val legalDocumentDataSource = mockk<LegalDocumentDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetLegalDocumentStatusImpl(legalDocumentDataSource, transactionManager)
    }

    @Test
    fun `should report when the user accepted each version in effect`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val policy = LegalDocumentVersion.stub(version = "2026-01-01")
        val terms = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE, version = "2025-06-01")
        val policyAcceptedAt = Instant.fromEpochSeconds(1_700_000_000)
        val termsAcceptedAt = Instant.fromEpochSeconds(1_750_000_000)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(policy, terms)
            coEvery { legalDocumentDataSource.getAcceptances(userId) } returns listOf(
                LegalDocumentAcceptance.stub(policy.document, policy.version, policyAcceptedAt),
                LegalDocumentAcceptance.stub(terms.document, terms.version, termsAcceptedAt),
            )
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertEquals(
            listOf(LegalDocumentStatus(policy, policyAcceptedAt), LegalDocumentStatus(terms, termsAcceptedAt)),
            result.getOrNull(),
        )
    }

    @Test
    fun `should report a version as not accepted when the user accepted only an earlier one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val policy = LegalDocumentVersion.stub(version = "2026-01-01")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(policy)
            coEvery { legalDocumentDataSource.getAcceptances(userId) } returns listOf(
                LegalDocumentAcceptance.stub(policy.document, version = "2025-01-01"),
            )
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertEquals(listOf(LegalDocumentStatus(policy, acceptedAt = null)), result.getOrNull())
    }

    @Test
    fun `should not match an acceptance of the same version of another document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val terms = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE, version = "2026-01-01")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(terms)
            coEvery { legalDocumentDataSource.getAcceptances(userId) } returns listOf(
                LegalDocumentAcceptance.stub(LegalDocument.PRIVACY_POLICY, version = terms.version),
            )
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertEquals(listOf(LegalDocumentStatus(terms, acceptedAt = null)), result.getOrNull())
    }

    @Test
    fun `should return no statuses when nothing is published`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns emptyList()
            coEvery { legalDocumentDataSource.getAcceptances(userId) } returns listOf(LegalDocumentAcceptance.stub())
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.getOrThrow().isEmpty())
    }
}
