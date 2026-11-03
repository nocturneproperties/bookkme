package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CreateUserImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val legalDocumentDataSource = mockk<LegalDocumentDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateUserImpl(userDataSource, legalDocumentDataSource, transactionManager)
    }

    private val policy = LegalDocumentVersion.stub(version = "2026-01-01")
    private val terms = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE, version = "2025-06-01")

    @Test
    fun `should insert user and return its id when nothing is published`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = User.stub(name = "Ann", lastName = "Lee", email = "ann@example.com")
        val inserted = slot<User>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns emptyList()
            coEvery { userDataSource.insertNewUser(capture(inserted)) } returns created
        }

        whenn()
        val result = fixture.sut.invoke(NewUser("Ann", "Lee", "ann@example.com", AcceptedLegalDocuments(null, null)))

        then()
        assertEquals(created.id, result.getOrNull()?.id)
        assertEquals(listOf("Ann", "Lee", "ann@example.com"), with(inserted.captured) { listOf(name, lastName, email) })
        assertEquals(null, inserted.captured.phone)
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should record the accepted versions in effect for the new user after creating it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = User.stub()
        val recorded = mutableListOf<LegalDocumentAcceptance>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(policy, terms)
            coEvery { legalDocumentDataSource.getVersion(policy.document, policy.version) } returns policy
            coEvery { legalDocumentDataSource.getVersion(terms.document, terms.version) } returns terms
            coEvery { userDataSource.insertNewUser(any()) } returns created
            coEvery { legalDocumentDataSource.recordAcceptance(created.id, capture(recorded)) } just runs
        }

        whenn()
        val result = fixture.sut.invoke(newUser(AcceptedLegalDocuments(policy.version, terms.version)))

        then()
        assertTrue(result.isSuccess)
        assertEquals(
            setOf(policy.document to policy.version, terms.document to terms.version),
            recorded.map { it.document to it.version }.toSet(),
        )
        coVerifyOrder {
            fixture.userDataSource.insertNewUser(any())
            fixture.legalDocumentDataSource.recordAcceptance(created.id, any())
        }
    }

    @Test
    fun `should fail with LegalDocumentsNotAccepted and create nothing when a document in effect is not accepted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(policy, terms)
            coEvery { legalDocumentDataSource.getVersion(policy.document, policy.version) } returns policy
        }

        whenn()
        val result = fixture.sut.invoke(newUser(AcceptedLegalDocuments(privacyPolicy = policy.version, termsOfService = null)))

        then()
        assertTrue(result.exceptionOrNull() is CreateUser.Error.LegalDocumentsNotAccepted)
        coVerify(exactly = 0) { fixture.userDataSource.insertNewUser(any()) }
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should fail with LegalDocumentsNotAccepted when an earlier version than the one in effect is accepted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val earlierPolicy = LegalDocumentVersion.stub(version = "2025-01-01")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(policy)
            coEvery { legalDocumentDataSource.getVersion(earlierPolicy.document, earlierPolicy.version) } returns earlierPolicy
        }

        whenn()
        val result = fixture.sut.invoke(newUser(AcceptedLegalDocuments(privacyPolicy = earlierPolicy.version, termsOfService = null)))

        then()
        assertTrue(result.exceptionOrNull() is CreateUser.Error.LegalDocumentsNotAccepted)
        coVerify(exactly = 0) { fixture.userDataSource.insertNewUser(any()) }
    }

    @Test
    fun `should fail with VersionUnknown and create nothing when an accepted version was never published`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns listOf(policy)
            coEvery { legalDocumentDataSource.getVersion(LegalDocument.PRIVACY_POLICY, "unpublished") } returns null
        }

        whenn()
        val result = fixture.sut.invoke(newUser(AcceptedLegalDocuments(privacyPolicy = "unpublished", termsOfService = null)))

        then()
        assertTrue(result.exceptionOrNull() is LegalDocumentError.VersionUnknown)
        coVerify(exactly = 0) { fixture.userDataSource.insertNewUser(any()) }
        coVerify(exactly = 0) { fixture.legalDocumentDataSource.recordAcceptance(any(), any()) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { legalDocumentDataSource.getVersionsInEffect(any()) } returns emptyList()
            coEvery { userDataSource.insertNewUser(any()) } throws RuntimeException("constraint violation")
        }

        whenn()
        val result = fixture.sut.invoke(newUser(AcceptedLegalDocuments(null, null)))

        then()
        assertTrue(result.isFailure)
    }

    private fun newUser(acceptedDocuments: AcceptedLegalDocuments) = NewUser(
        name = "Alice",
        lastName = "Smith",
        email = "user@example.com",
        acceptedDocuments = acceptedDocuments,
    )
}
