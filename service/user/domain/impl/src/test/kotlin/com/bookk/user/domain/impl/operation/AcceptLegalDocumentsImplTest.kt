package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class AcceptLegalDocumentsImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val legalDocumentDataSource = mockk<LegalDocumentDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = AcceptLegalDocumentsImpl(userDataSource, legalDocumentDataSource, transactionManager)
    }

    @Test
    fun `should record an acceptance of each sent version`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val policy = LegalDocumentVersion.stub(version = "2026-01-01")
        val terms = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE, version = "2025-06-01")
        val recorded = mutableListOf<LegalDocumentAcceptance>()
        val recordedInTransaction = mutableListOf<Boolean>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns User.stub(id = userId)
            coEvery { legalDocumentDataSource.getVersion(policy.document, policy.version) } returns policy
            coEvery { legalDocumentDataSource.getVersion(terms.document, terms.version) } returns terms
            coEvery { legalDocumentDataSource.recordAcceptance(userId, capture(recorded)) } coAnswers {
                recordedInTransaction.add(transactionManager.isInTransaction())
            }
        }
        val before = Clock.System.now()

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(policy.version, terms.version))

        then()
        assertTrue(result.isSuccess)
        assertEquals(
            setOf(policy.document to policy.version, terms.document to terms.version),
            recorded.map { it.document to it.version }.toSet(),
        )
        assertTrue(recorded.all { it.acceptedAt >= before })
        assertEquals(listOf(true, true), recordedInTransaction)
    }

    @Test
    fun `should record only the documents that were sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val terms = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE)
        val recorded = slot<LegalDocumentAcceptance>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns User.stub(id = userId)
            coEvery { legalDocumentDataSource.getVersion(terms.document, terms.version) } returns terms
            coEvery { legalDocumentDataSource.recordAcceptance(userId, capture(recorded)) } just runs
        }

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(privacyPolicy = null, termsOfService = terms.version))

        then()
        assertTrue(result.isSuccess)
        assertEquals(LegalDocument.TERMS_OF_SERVICE, recorded.captured.document)
        coVerify(exactly = 1) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should fail with VersionUnknown when a version was never published`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns User.stub(id = userId)
            coEvery { legalDocumentDataSource.getVersion(LegalDocument.PRIVACY_POLICY, "unpublished") } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(privacyPolicy = "unpublished", termsOfService = null))

        then()
        assertTrue(result.exceptionOrNull() is LegalDocumentError.VersionUnknown)
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should fail with VersionUnknown when a version takes effect later`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val upcoming = LegalDocumentVersion.stub(effectiveAt = Clock.System.now() + 10.days)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns User.stub(id = userId)
            coEvery { legalDocumentDataSource.getVersion(upcoming.document, upcoming.version) } returns upcoming
        }

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(privacyPolicy = upcoming.version, termsOfService = null))

        then()
        assertTrue(result.exceptionOrNull() is LegalDocumentError.VersionUnknown)
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should record nothing when one of the sent versions is unknown`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val policy = LegalDocumentVersion.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns User.stub(id = userId)
            coEvery { legalDocumentDataSource.getVersion(policy.document, policy.version) } returns policy
            coEvery { legalDocumentDataSource.getVersion(LegalDocument.TERMS_OF_SERVICE, "unpublished") } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(privacyPolicy = policy.version, termsOfService = "unpublished"))

        then()
        assertTrue(result.exceptionOrNull() is LegalDocumentError.VersionUnknown)
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should fail with NotFound when the user does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = null))

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should succeed without recording when no document is sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.getUserById(userId) } returns User.stub(id = userId)
        }

        whenn()
        val result = fixture.sut.invoke(userId, AcceptedLegalDocuments(privacyPolicy = null, termsOfService = null))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }
}
