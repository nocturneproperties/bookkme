package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.PassKeyDataSource
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
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class GetAvailablePasskeysImplTest {

    private class SutFixture {
        val passKeyDataSource = mockk<PassKeyDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAvailablePasskeysImpl(passKeyDataSource, transactionManager)
    }

    private fun makePasskeyCredential(authId: Uuid): PasskeyCredential {
        val now = Clock.System.now()
        return PasskeyCredential(
            id = Uuid.random(),
            authId = authId,
            authInfo = Authentication(id = Uuid.random(), userId = Uuid.random(), uuid = Uuid.random()),
            handle = Uuid.random(),
            name = "My iPhone",
            credDescriptor = PasskeyCredential.CredentialDescriptor(ByteArray(0), "public-key", emptySet()),
            publicKey = "key",
            signatureCount = 5,
            isDiscoverable = true,
            isBackupEligible = true,
            isBackedUp = true,
            attestationObject = ByteArray(0),
            clientData = "{}",
            createdAt = now,
            lastUsedAt = now
        )
    }

    @Test
    fun `should return mapped passkey responses for auth id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val credentials = listOf(makePasskeyCredential(authId), makePasskeyCredential(authId))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.getCredentialBy(authId) } returns credentials
        }

        whenn()
        val result = fixture.sut.invoke(authId)

        then()
        assertTrue(result.isSuccess)
        val responses = result.getOrNull()!!
        assertEquals(2, responses.size)
        assertEquals(credentials[0].id, responses[0].id)
        assertEquals(credentials[0].name, responses[0].name)
        assertEquals(credentials[0].isBackedUp, responses[0].isBackedUp)
    }

    @Test
    fun `should return empty list when no passkeys exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.getCredentialBy(authId) } returns emptyList()
        }

        whenn()
        val result = fixture.sut.invoke(authId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrNull())
    }
}
