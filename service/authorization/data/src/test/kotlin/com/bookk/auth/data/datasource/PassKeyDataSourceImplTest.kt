package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.data.orm.table.PasskeyCredentialTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class PassKeyDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AuthenticationTable, PasskeyCredentialTable)
        val cacheClient = InMemoryCacheClient()
        val sut = PassKeyDataSourceImpl(cacheClient)
        val accountSut = AccountDataSourceImpl()

        suspend fun insertAuth(): Authentication {
            return suspendTransaction { accountSut.createAuthorization(Authentication.stub()) }
        }
    }

    @Test
    fun `should create passkey credential and retrieve by auth id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val credential = PasskeyCredential.stub(authInfo = auth)

        whenn()
        suspendTransaction { fixture.sut.createPasskeyCredential(credential) }
        val found = suspendTransaction { fixture.sut.getCredentialBy(auth.id) }

        then()
        assertEquals(1, found.size)
        assertEquals(credential.authId, found.first().authId)
        assertEquals("My key", found.first().name)
    }

    @Test
    fun `should return empty list when no credentials for auth`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getCredentialBy(Uuid.random()) }

        then()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `should retrieve credential by user handle and credential id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val credential = PasskeyCredential.stub(authInfo = auth)
        suspendTransaction { fixture.sut.createPasskeyCredential(credential) }

        whenn()
        val found = suspendTransaction {
            fixture.sut.getCredentialBy(auth.uuid, credential.credDescriptor.id)
        }

        then()
        assertNotNull(found)
        assertEquals(auth.id, found!!.authId)
    }

    @Test
    fun `should return null when credential not found by handle and id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()

        whenn()
        val found = suspendTransaction {
            fixture.sut.getCredentialBy(auth.uuid, ByteArray(24) { 99 })
        }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve credentials by username`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        suspendTransaction { fixture.sut.createPasskeyCredential(PasskeyCredential.stub(authInfo = auth)) }

        whenn()
        val found = suspendTransaction { fixture.sut.getCredentialsByUsername(auth.uuid) }

        then()
        assertEquals(1, found.size)
    }

    @Test
    fun `should retrieve credentials by credential id bytes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val credential = PasskeyCredential.stub(authInfo = auth)
        suspendTransaction { fixture.sut.createPasskeyCredential(credential) }

        whenn()
        val found = suspendTransaction { fixture.sut.getCredentialsByCredentialId(credential.credDescriptor.id) }

        then()
        assertEquals(1, found.size)
    }

    @Test
    fun `should mark passkey as used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        suspendTransaction { fixture.sut.createPasskeyCredential(PasskeyCredential.stub(authInfo = auth)) }
        val created = suspendTransaction { fixture.sut.getCredentialBy(auth.id) }.first()

        whenn()
        suspendTransaction { fixture.sut.markAsUsed(created.id) }

        then()
        val updated = suspendTransaction { fixture.sut.getCredentialBy(auth.id) }.first()
        assertTrue(updated.lastUsedAt >= created.lastUsedAt)
    }

    @Test
    fun `should not delete last passkey to preserve account access`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        suspendTransaction { fixture.sut.createPasskeyCredential(PasskeyCredential.stub(authInfo = auth)) }
        val created = suspendTransaction { fixture.sut.getCredentialBy(auth.id) }.first()

        whenn()
        val deleted = suspendTransaction { fixture.sut.deletePasskey(created.id, auth.id) }

        then()
        assertEquals(0, deleted)
    }

    @Test
    fun `should delete passkey when multiple exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        suspendTransaction {
            fixture.sut.createPasskeyCredential(PasskeyCredential.stub(authInfo = auth))
            fixture.sut.createPasskeyCredential(
                PasskeyCredential.stub(
                    authInfo = auth,
                    credDescriptor = PasskeyCredential.CredentialDescriptor(
                        id = ByteArray(24) { (it + 100).toByte() },
                        type = "public-key",
                        transports = setOf("internal")
                    )
                )
            )
        }
        val first = suspendTransaction { fixture.sut.getCredentialBy(auth.id) }.first()

        whenn()
        val deleted = suspendTransaction { fixture.sut.deletePasskey(first.id, auth.id) }

        then()
        assertEquals(1, deleted)
        assertEquals(1, suspendTransaction { fixture.sut.getCredentialBy(auth.id) }.size)
    }

    @Test
    fun `should return handle for existing user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()

        whenn()
        val handle = suspendTransaction { fixture.sut.getHandleByUsername(auth.uuid) }

        then()
        assertEquals(auth.uuid, handle)
    }

    @Test
    fun `should return null handle for unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val handle = suspendTransaction { fixture.sut.getHandleByUsername(Uuid.random()) }

        then()
        assertNull(handle)
    }

    @Test
    fun `should delegate saveChallengeToCache to cache client`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.saveChallengeToCache("req-1", "challenge-abc")

        then()
        val inCache: String? = fixture.cacheClient.get("req-1")
        assertEquals("challenge-abc", inCache)
    }

    @Test
    fun `should return null from getCachedChallenge on cache miss`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.getCachedChallenge("req-1")

        then()
        assertNull(result)
    }
}
