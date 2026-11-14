package com.bookk.user.data.datasource

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.orm.table.LegalDocumentAcceptanceTable
import com.bookk.user.data.orm.table.LegalDocumentVersionTable
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.api.entity.User
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class LegalDocumentDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(UserTable, LegalDocumentVersionTable, LegalDocumentAcceptanceTable)
        val profileCipher = ProfileCipher(InMemorySubjectKeys(), UnkeyedBlindIndex(), RecordingMissingKeys())
        val userDataSource = UserDataSourceImpl(InMemoryCacheClient(), profileCipher)
        val sut = LegalDocumentDataSourceImpl()

        suspend fun publish(version: LegalDocumentVersion) = suspendTransaction {
            LegalDocumentVersionTable.insert {
                it[document] = version.document
                it[this.version] = version.version
                it[url] = version.url
                it[effectiveAt] = version.effectiveAt
            }
        }

        suspend fun createUser(): Uuid = suspendTransaction {
            userDataSource.insertNewUser(User.stub(email = "${Uuid.random()}@example.com")).id
        }
    }

    @Test
    fun `should return the latest version in effect of each document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val oldPolicy = LegalDocumentVersion.stub(version = "2025-01-01", effectiveAt = now - 300.days)
        val currentPolicy = LegalDocumentVersion.stub(version = "2026-01-01", effectiveAt = now - 10.days)
        val currentTerms = LegalDocumentVersion.stub(
            document = LegalDocument.TERMS_OF_SERVICE,
            version = "2025-06-01",
            url = "https://bookk.me/terms",
            effectiveAt = now - 100.days,
        )
        listOf(oldPolicy, currentPolicy, currentTerms).forEach { fixture.publish(it) }

        whenn()
        val versions = suspendTransaction { fixture.sut.getVersionsInEffect(now) }

        then()
        assertEquals(setOf(currentPolicy, currentTerms), versions.toSet())
    }

    @Test
    fun `should not return a version that takes effect later`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val currentPolicy = LegalDocumentVersion.stub(version = "2026-01-01", effectiveAt = now - 10.days)
        val upcomingPolicy = LegalDocumentVersion.stub(version = "2026-12-01", effectiveAt = now + 10.days)
        listOf(currentPolicy, upcomingPolicy).forEach { fixture.publish(it) }

        whenn()
        val versions = suspendTransaction { fixture.sut.getVersionsInEffect(now) }

        then()
        assertEquals(listOf(currentPolicy), versions)
    }

    @Test
    fun `should return no versions when nothing is published`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val versions = suspendTransaction { fixture.sut.getVersionsInEffect(Clock.System.now()) }

        then()
        assertTrue(versions.isEmpty())
    }

    @Test
    fun `should find a published version by document and version`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val policy = LegalDocumentVersion.stub(effectiveAt = Instant.fromEpochSeconds(1_700_000_000))
        fixture.publish(policy)

        whenn()
        val found = suspendTransaction { fixture.sut.getVersion(policy.document, policy.version) }

        then()
        assertEquals(policy, found)
    }

    @Test
    fun `should not find the same version of another document`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val policy = LegalDocumentVersion.stub()
        fixture.publish(policy)

        whenn()
        val found = suspendTransaction { fixture.sut.getVersion(LegalDocument.TERMS_OF_SERVICE, policy.version) }

        then()
        assertNull(found)
    }

    @Test
    fun `should record acceptances and return them for that user only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val policy = LegalDocumentVersion.stub()
        val terms = LegalDocumentVersion.stub(document = LegalDocument.TERMS_OF_SERVICE, url = "https://bookk.me/terms")
        listOf(policy, terms).forEach { fixture.publish(it) }
        val userId = fixture.createUser()
        val otherUserId = fixture.createUser()
        val acceptedAt = Instant.fromEpochSeconds(1_700_000_000)
        val policyAcceptance = LegalDocumentAcceptance.stub(policy.document, policy.version, acceptedAt)
        val termsAcceptance = LegalDocumentAcceptance.stub(terms.document, terms.version, acceptedAt)

        whenn()
        suspendTransaction {
            fixture.sut.recordAcceptance(userId, policyAcceptance)
            fixture.sut.recordAcceptance(userId, termsAcceptance)
            fixture.sut.recordAcceptance(otherUserId, policyAcceptance)
        }
        val acceptances = suspendTransaction { fixture.sut.getAcceptances(userId) }

        then()
        assertEquals(setOf(policyAcceptance, termsAcceptance), acceptances.toSet())
    }

    @Test
    fun `should keep the first acceptance time when the same version is accepted again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val policy = LegalDocumentVersion.stub()
        fixture.publish(policy)
        val userId = fixture.createUser()
        val first = LegalDocumentAcceptance.stub(
            policy.document,
            policy.version,
            Instant.fromEpochSeconds(1_700_000_000),
        )
        val second = first.copy(acceptedAt = Instant.fromEpochSeconds(1_800_000_000))

        whenn()
        suspendTransaction { fixture.sut.recordAcceptance(userId, first) }
        suspendTransaction { fixture.sut.recordAcceptance(userId, second) }
        val acceptances = suspendTransaction { fixture.sut.getAcceptances(userId) }

        then()
        assertEquals(listOf(first), acceptances)
    }

    @Test
    fun `should keep the acceptances of every version`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val oldPolicy = LegalDocumentVersion.stub(version = "2025-01-01")
        val newPolicy = LegalDocumentVersion.stub(version = "2026-01-01")
        listOf(oldPolicy, newPolicy).forEach { fixture.publish(it) }
        val userId = fixture.createUser()
        val oldAcceptance = LegalDocumentAcceptance.stub(
            version = oldPolicy.version,
            acceptedAt = Instant.fromEpochSeconds(1_700_000_000),
        )
        val newAcceptance = LegalDocumentAcceptance.stub(
            version = newPolicy.version,
            acceptedAt = Instant.fromEpochSeconds(1_800_000_000),
        )

        whenn()
        suspendTransaction {
            fixture.sut.recordAcceptance(userId, oldAcceptance)
            fixture.sut.recordAcceptance(userId, newAcceptance)
        }
        val acceptances = suspendTransaction { fixture.sut.getAcceptances(userId) }

        then()
        assertEquals(setOf(oldAcceptance, newAcceptance), acceptances.toSet())
    }

    @Test
    fun `should delete a user's acceptances together with the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val policy = LegalDocumentVersion.stub()
        fixture.publish(policy)
        val userId = fixture.createUser()
        suspendTransaction { fixture.sut.recordAcceptance(userId, LegalDocumentAcceptance.stub()) }

        whenn()
        suspendTransaction { fixture.userDataSource.deleteUser(userId) }
        val acceptances = suspendTransaction { fixture.sut.getAcceptances(userId) }

        then()
        assertTrue(acceptances.isEmpty())
    }

    @Test
    fun `should reject an acceptance of a version that was never published`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = fixture.createUser()

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.recordAcceptance(userId, LegalDocumentAcceptance.stub(version = "unpublished"))
            }
        }

        then()
        assertTrue(result.isFailure)
    }
}
